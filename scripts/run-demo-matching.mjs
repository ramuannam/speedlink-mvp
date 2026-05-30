import crypto from "node:crypto";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import net from "node:net";
import path from "node:path";

const rootDir = path.resolve(import.meta.dirname, "..");
const envPath = path.join(rootDir, "backend", ".env.local");

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) {
    return;
  }
  for (const line of fs.readFileSync(filePath, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#") || !trimmed.includes("=")) {
      continue;
    }
    const [key, ...valueParts] = trimmed.split("=");
    process.env[key.trim()] ??= valueParts.join("=").trim();
  }
}

function base64url(input) {
  return Buffer.from(input).toString("base64url");
}

function issueToken(userId, secret, ttlMinutes) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = base64url(JSON.stringify({
    sub: userId,
    iat: now,
    exp: now + ttlMinutes * 60,
  }));
  const signature = crypto.createHmac("sha256", secret).update(`${header}.${payload}`).digest("base64url");
  return `${header}.${payload}.${signature}`;
}

function queryProfiles() {
  const psql = process.env.PSQL_PATH || "C:\\Program Files\\PostgreSQL\\17\\bin\\psql.exe";
  const sql = `
    select coalesce(json_agg(row_to_json(t)), '[]'::json)
    from (
      select
        id as "userId",
        email,
        display_name as "displayName",
        role,
        looking_for as "lookingFor",
        expertise,
        goals,
        intent,
        bio,
        interests,
        company_type as "companyType",
        age_range as "ageRange",
        linkedin_url as "linkedinUrl",
        portfolio_url as "portfolioUrl",
        location,
        experience_level as "experienceLevel",
        availability,
        profile_photo as "profilePhoto"
      from user_accounts
      where email like 'demo%@speedlink.local'
      order by email
      limit 50
    ) t;
  `;
  const output = execFileSync(psql, [
    "-h", process.env.DB_HOST || "localhost",
    "-p", process.env.DB_PORT || "5432",
    "-U", process.env.DB_USERNAME || "speedlink",
    "-d", process.env.DB_NAME || "speedlink",
    "-t",
    "-A",
    "-c",
    sql,
  ], {
    env: { ...process.env, PGPASSWORD: process.env.DB_PASSWORD || "password" },
    encoding: "utf8",
  }).trim();
  return JSON.parse(output);
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function redisCommand(socket, args) {
  const payload = `*${args.length}\r\n${args.map((arg) => `$${Buffer.byteLength(String(arg))}\r\n${arg}\r\n`).join("")}`;
  return new Promise((resolve, reject) => {
    const onData = (chunk) => {
      socket.off("error", onError);
      resolve(chunk.toString("utf8"));
    };
    const onError = (error) => {
      socket.off("data", onData);
      reject(error);
    };
    socket.once("data", onData);
    socket.once("error", onError);
    socket.write(payload);
  });
}

function parseRedisArray(response) {
  return [...response.matchAll(/\$\d+\r\n([^\r\n]+)/g)].map((match) => match[1]);
}

async function clearRealtimeRedisKeys() {
  const socket = net.createConnection({
    host: process.env.REDIS_HOST || "localhost",
    port: Number(process.env.REDIS_PORT || 6379),
  });
  await new Promise((resolve, reject) => {
    socket.once("connect", resolve);
    socket.once("error", reject);
  });
  const keysResponse = await redisCommand(socket, ["KEYS", "speedlink:*"]);
  const keys = parseRedisArray(keysResponse);
  if (keys.length) {
    await redisCommand(socket, ["DEL", ...keys]);
  }
  socket.end();
  return keys.length;
}

function table(rows) {
  const headers = ["#", "roomId", "emailA", "emailB", "matchId"];
  const widths = headers.map((header) =>
    Math.max(header.length, ...rows.map((row) => String(row[header] ?? "").length)),
  );
  const format = (row) => headers.map((header, index) => String(row[header] ?? "").padEnd(widths[index])).join("  ");
  return [format(Object.fromEntries(headers.map((header) => [header, header]))), ...rows.map(format)].join("\n");
}

loadEnvFile(envPath);

const secret = process.env.SPEEDLINK_JWT_SECRET || "change-me";
const ttlMinutes = Number(process.env.SPEEDLINK_TOKEN_TTL_MINUTES || 720);
const wsUrl = process.env.VITE_WS_URL || "ws://localhost:8080/ws";
const burstMode = process.argv.includes("--burst");
const advancedMode = process.argv.includes("--advanced");
const matchingMode = advancedMode ? "advanced" : "basic";
const profiles = queryProfiles();
const clients = new Map();
const rooms = new Map();
const errors = [];
const samples = [];
const cancellations = [];

if (profiles.length !== 50) {
  throw new Error(`Expected 50 demo users in DB, found ${profiles.length}.`);
}

console.log(`Realtime matching run started: ${new Date().toISOString()}`);
console.log(`Profiles loaded from DB: ${profiles.length}`);
console.log(`Mode: local SpeedLink JWT + real WebSocket joinQueue/${matchingMode} matching`);
console.log(`Search cadence: ${burstMode ? "all 50 users at once" : "two demo users at a time"}`);
try {
  const cleared = await clearRealtimeRedisKeys();
  console.log(`Cleared realtime Redis keys: ${cleared}`);
} catch (error) {
  console.log(`Cleared realtime Redis keys: skipped (${error.message})`);
}

for (const row of profiles) {
  const { email, ...profile } = row;
  clients.set(profile.userId, {
    email,
    profile,
    token: issueToken(profile.userId, secret, ttlMinutes),
    connected: false,
    offered: false,
    socket: null,
  });
}

await Promise.all([...clients.values()].map((client) => new Promise((resolve) => {
  const socket = new WebSocket(`${wsUrl}?token=${encodeURIComponent(client.token)}`);
  client.socket = socket;

  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    if (samples.length < 20 && ["match-offer", "match-cancelled", "match-accepted", "call-started"].includes(message.type)) {
      samples.push(`${client.email}: ${JSON.stringify(message)}`);
    }
    if (message.type === "connected") {
      client.connected = true;
    }
    if (message.type === "match-offer") {
      client.offered = true;
      socket.send(JSON.stringify({ type: "acceptMatch", matchId: message.payload.matchId }));
    }
    if (message.type === "call-started") {
      const peer = message.payload.peer;
      const peerClient = clients.get(peer.userId);
      const emails = [client.email, peerClient?.email || peer.displayName].sort();
      client.roomId = message.payload.roomId;
      rooms.set(message.payload.roomId, {
        "#": 0,
        roomId: message.payload.roomId,
        emailA: emails[0],
        emailB: emails[1],
        matchId: message.payload.matchId,
      });
    }
    if (message.type === "error") {
      errors.push(`${client.email}: ${message.payload?.message || "unknown error"}`);
    }
    if (message.type === "match-cancelled") {
      cancellations.push(`${client.email}: ${message.payload?.reason || "cancelled"}`);
    }
  });

  socket.addEventListener("error", () => {
    errors.push(`${client.email}: websocket error`);
  });

  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    if (message.type === "connected") {
      resolve();
    }
  });
})));

const orderedClients = [...clients.values()].sort((left, right) => left.email.localeCompare(right.email));

if (burstMode) {
  for (const client of orderedClients) {
    client.socket.send(JSON.stringify({
      type: "joinQueue",
      profile: client.profile,
      matchingMode,
    }));
  }
  const deadline = Date.now() + 45_000;
  while (rooms.size < 25 && Date.now() < deadline) {
    await wait(100);
  }
} else {
  for (let index = 0; index < orderedClients.length; index += 2) {
    const pair = [orderedClients[index], orderedClients[index + 1]].filter(Boolean);
    const before = rooms.size;
    for (const client of pair) {
      client.socket.send(JSON.stringify({
        type: "joinQueue",
        profile: client.profile,
        matchingMode,
      }));
    }
    const deadline = Date.now() + 8_000;
    while (rooms.size === before && Date.now() < deadline) {
      await wait(100);
    }
  }
}

const rows = [...rooms.values()]
  .sort((left, right) => left.emailA.localeCompare(right.emailA))
  .map((row, index) => ({ ...row, "#": index + 1 }));

console.log("\nROOM PAIRS");
console.log(table(rows));
console.log("\nSUMMARY");
console.log(`Connected sockets: ${[...clients.values()].filter((client) => client.connected).length}/50`);
console.log(`Match offers received: ${[...clients.values()].filter((client) => client.offered).length}/50`);
console.log(`Active room rows observed: ${rows.length}/25`);
console.log(`Match cancellations observed: ${cancellations.length}`);
console.log(`Errors observed: ${errors.length}`);
if (samples.length) {
  console.log("\nMESSAGE SAMPLES");
  console.log(samples.join("\n"));
}
if (errors.length) {
  console.log(errors.slice(0, 12).join("\n"));
}

await wait(2_000);
const endedRooms = new Set();
for (const client of clients.values()) {
  if (client.roomId && !endedRooms.has(client.roomId)) {
    client.socket?.send(JSON.stringify({ type: "endCall", roomId: client.roomId }));
    endedRooms.add(client.roomId);
  }
}
await wait(500);
for (const client of clients.values()) {
  client.socket?.close();
}
process.exit(0);
