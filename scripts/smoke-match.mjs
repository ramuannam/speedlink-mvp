const apiUrl = process.env.SPEEDLINK_API || "http://localhost:8080/api";
const wsEndpoint = process.env.SPEEDLINK_WS || "ws://localhost:8080/ws";

async function signup(profile) {
  const response = await fetch(`${apiUrl}/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(profile),
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.message || "Signup failed");
  }
  return data;
}

function createClient(label, token) {
  const client = {
    label,
    ws: new WebSocket(`${wsEndpoint}?token=${encodeURIComponent(token)}`),
    messages: [],
    listeners: [],
  };

  client.ws.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    client.messages.push(message);

    for (const listener of [...client.listeners]) {
      if (listener.type === message.type) {
        clearTimeout(listener.timer);
        client.listeners = client.listeners.filter((entry) => entry !== listener);
        listener.resolve(message.payload);
      }
    }
  });

  client.send = (message) => {
    client.ws.send(JSON.stringify(message));
  };

  client.waitFor = (type, timeoutMs = 7000) => {
    const existing = client.messages.find((message) => message.type === type);
    if (existing) {
      return Promise.resolve(existing.payload);
    }

    return new Promise((resolve, reject) => {
      const listener = {
        type,
        resolve,
        timer: setTimeout(() => {
          client.listeners = client.listeners.filter((entry) => entry !== listener);
          reject(new Error(`${label} did not receive ${type}`));
        }, timeoutMs),
      };
      client.listeners.push(listener);
    });
  };

  client.close = () => client.ws.close();

  return client;
}

async function main() {
  const runId = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const developerAccount = await signup({
    email: `dev-${runId}@speedlink.test`,
    password: "StrongPass123",
    displayName: "Dev Smoke",
    role: "Developer",
    lookingFor: "Designer",
    expertise: "React and Java",
    goals: "Build an MVP",
    intent: "Product sprint",
  });

  const designerAccount = await signup({
    email: `designer-${runId}@speedlink.test`,
    password: "StrongPass123",
    displayName: "Design Smoke",
    role: "Designer",
    lookingFor: "Developer",
    expertise: "Product design and prototypes",
    goals: "Ship a polished MVP",
    intent: "Build partner",
  });

  const developer = createClient("developer", developerAccount.token);
  const designer = createClient("designer", designerAccount.token);

  await Promise.all([developer.waitFor("connected"), designer.waitFor("connected")]);

  developer.send({ type: "joinQueue", profile: developerAccount.profile });
  designer.send({ type: "joinQueue", profile: designerAccount.profile });

  const [developerOffer, designerOffer] = await Promise.all([
    developer.waitFor("match-offer"),
    designer.waitFor("match-offer"),
  ]);

  developer.send({ type: "acceptMatch", matchId: developerOffer.matchId });
  designer.send({ type: "acceptMatch", matchId: designerOffer.matchId });

  const [developerCall, designerCall] = await Promise.all([
    developer.waitFor("call-started"),
    designer.waitFor("call-started"),
  ]);

  if (developerCall.roomId !== designerCall.roomId) {
    throw new Error("Matched users did not receive the same room id");
  }

  developer.send({ type: "endCall", roomId: developerCall.roomId });
  developer.close();
  designer.close();

  console.log(`Auth smoke match passed: ${developerCall.roomId}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
