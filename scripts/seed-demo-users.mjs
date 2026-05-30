import fs from "node:fs";
import path from "node:path";
import { createClient } from "../frontend/node_modules/@supabase/supabase-js/dist/index.mjs";

const rootDir = path.resolve(import.meta.dirname, "..");
const backendEnvPath = path.join(rootDir, "backend", ".env.local");
const outputPath = path.join(rootDir, "demo-users.csv");

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

function csvEscape(value) {
  return `"${String(value ?? "").replaceAll('"', '""')}"`;
}

function profileFor(index) {
  const roles = ["Developer", "Designer", "Founder", "Co-founder", "Product Manager", "Data Scientist", "Marketer", "Freelancer"];
  const lookingFor = ["Designer", "Developer", "Founder", "Co-founder", "Product Manager", "Data Scientist", "Marketer", "Anyone/Random"];
  const interests = ["Build projects", "Career advice", "Hiring", "Investors", "Explore New People", "Random Friendly Conversation"];
  const companyTypes = ["Startup", "Freelancer", "Student", "MNC"];
  const experienceLevels = ["Student", "0-1 years", "2-4 years", "5-8 years", "9+ years"];
  const availability = ["Available now", "Weekdays", "Weekends", "Evenings", "Flexible"];
  const cities = ["Bengaluru", "Mumbai", "Delhi", "Hyderabad", "Pune", "Chennai", "Ahmedabad", "Remote"];
  const role = roles[index % roles.length];
  const target = lookingFor[(index + 2) % lookingFor.length];
  const interest = interests[(index + 3) % interests.length];

  return {
    displayName: `Demo ${role} ${String(index + 1).padStart(2, "0")}`,
    role,
    lookingFor: target,
    expertise: `${role} workflows, MVP launches, realtime collaboration`,
    goals: `Meet a ${target.toLowerCase()} for practical product and startup conversations.`,
    intent: interest,
    bio: `Demo profile ${index + 1} for local SpeedLink realtime matching tests.`,
    interests: interest,
    companyType: companyTypes[index % companyTypes.length],
    ageRange: index % 5 === 0 ? "18-24" : index % 5 === 1 ? "25-34" : index % 5 === 2 ? "35-44" : "",
    linkedinUrl: `https://linkedin.com/in/speedlink-demo-${String(index + 1).padStart(2, "0")}`,
    portfolioUrl: `https://speedlink-demo-${String(index + 1).padStart(2, "0")}.example.com`,
    location: `${cities[index % cities.length]}, India`,
    experienceLevel: experienceLevels[index % experienceLevels.length],
    availability: availability[index % availability.length],
    profilePhoto: "",
  };
}

async function apiRequest(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      ...(options.headers || {}),
    },
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`${response.status} ${response.statusText}: ${body}`);
  }
  return response.status === 204 ? null : response.json();
}

loadEnvFile(backendEnvPath);

const supabaseUrl = process.env.SUPABASE_URL;
const serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const publishableKey = process.env.SUPABASE_PUBLISHABLE_KEY || process.env.SUPABASE_ANON_KEY;
const apiUrl = process.env.VITE_API_URL || "http://localhost:8080/api";
const password = process.env.DEMO_USER_PASSWORD || "SpeedLink@2026!";

if (!supabaseUrl || !serviceRoleKey || !publishableKey) {
  throw new Error("SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, and SUPABASE_PUBLISHABLE_KEY are required.");
}

const admin = createClient(supabaseUrl, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});
const client = createClient(supabaseUrl, publishableKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const credentials = [];

for (let index = 0; index < 50; index += 1) {
  const number = String(index + 1).padStart(2, "0");
  const email = `demo${number}@speedlink.local`;
  const phone = `+91988000${String(index + 1).padStart(4, "0")}`;
  const profile = profileFor(index);

  const { data: created, error: createError } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    phone_confirm: true,
    user_metadata: {
      full_name: profile.displayName,
      whatsapp_phone: phone,
    },
  });

  if (createError && !/already|registered|exists/i.test(createError.message)) {
    throw new Error(`Could not create ${email}: ${createError.message}`);
  }

  if (createError) {
    const { data: users, error: listError } = await admin.auth.admin.listUsers({ page: 1, perPage: 1000 });
    if (listError) {
      throw new Error(`Could not find existing ${email}: ${listError.message}`);
    }
    const existing = users.users.find((user) => user.email?.toLowerCase() === email);
    if (!existing) {
      throw new Error(`Supabase said ${email} exists, but listUsers did not return it.`);
    }
    const { error: updateError } = await admin.auth.admin.updateUserById(existing.id, {
      password,
      email_confirm: true,
      phone_confirm: true,
      user_metadata: {
        full_name: profile.displayName,
        whatsapp_phone: phone,
      },
    });
    if (updateError) {
      throw new Error(`Could not update ${email}: ${updateError.message}`);
    }
  } else if (!created?.user?.id) {
    throw new Error(`Supabase did not return a user for ${email}.`);
  }

  const { data: sessionData, error: signInError } = await client.auth.signInWithPassword({ email, password });
  if (signInError || !sessionData?.session?.access_token) {
    throw new Error(`Could not sign in ${email}: ${signInError?.message || "missing session"}`);
  }

  const authResponse = await apiRequest(`${apiUrl}/auth/supabase`, {
    method: "POST",
    body: JSON.stringify({ accessToken: sessionData.session.access_token }),
  });
  await apiRequest(`${apiUrl}/auth/profile`, {
    method: "PUT",
    token: authResponse.token,
    body: JSON.stringify(profile),
  });

  credentials.push({ email, password, displayName: profile.displayName, role: profile.role, lookingFor: profile.lookingFor });
  console.log(`Seeded ${number}/50 ${email} (${profile.role} -> ${profile.lookingFor})`);
}

fs.writeFileSync(
  outputPath,
  [
    ["email", "password", "displayName", "role", "lookingFor"].map(csvEscape).join(","),
    ...credentials.map((row) => [row.email, row.password, row.displayName, row.role, row.lookingFor].map(csvEscape).join(",")),
  ].join("\n") + "\n",
);

console.log(`\nDemo users ready: ${credentials.length}`);
console.log(`Credentials written to ${outputPath}`);
