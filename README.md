# SpeedLink MVP

SpeedLink is a minimal full-stack MVP for real-time professional matching. Developers, designers, co-founders, freelancers, and other professionals can sign up, save profile details, enter a live queue, receive a compatible match, accept or reject within 15 seconds, and start a browser video session when both accept.

## Stack

- Java 17 + Spring Boot WebSocket backend
- React + Vite frontend
- WebRTC peer video with backend WebSocket signaling
- H2/PostgreSQL persistence for SpeedLink profile data
- Supabase Auth for email confirmation, password reset, passwords, and identity; SpeedLink issues short-lived app bearer tokens after Supabase verification
- In-memory live queue/match/call state for MVP simplicity

## Run locally

For quick backend experiments, the default Spring profile uses an H2 file database.
For production-like local development, use the `local` profile with Docker Postgres and Redis.

Start local infrastructure:

```powershell
docker-compose up -d postgres redis
```

Set your dev Supabase variables in the terminal, then start the backend with the local profile:

```powershell
$env:SUPABASE_URL="https://your-dev-project-ref.supabase.co"
$env:SUPABASE_PUBLISHABLE_KEY="your-dev-supabase-publishable-key"
.\scripts\start-backend-local.ps1
```

Start the frontend in another terminal:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Open `http://localhost:5173`. Sign up two accounts in two browser tabs or browser profiles to test matching. For example, one profile can be a Developer looking for a Designer, and the second can be a Designer looking for a Developer.

Run an auth + matching smoke test while the backend is running:

```powershell
node scripts/smoke-match.mjs
```

## Production-ready deployment

Use PostgreSQL, Redis, and JWT authentication for a production-grade backend. The Docker Compose stack now includes Redis for distributed queue and pending-match state.

1. Copy the example environment file:

```powershell
copy .env.example .env
```

2. Update `SPEEDLINK_JWT_SECRET`, `SUPABASE_URL`, and `SUPABASE_PUBLISHABLE_KEY` in `.env`.

3. Start the stack with Docker Compose:

```powershell
docker-compose up --build
```

4. Open the frontend at `http://localhost:5173` and the backend API at `http://localhost:8080`.

> Recommendation: Sticky session routing or cross-instance signaling is recommended for production scaling, because WebSocket connections and active room socket sessions remain local to each backend instance.

## Backend API

- `GET /api/health` returns backend health.
- `GET /api/stats` returns live in-memory counts.
- `POST /api/auth/verification-link` checks whether signup/reset is allowed before Supabase sends the email link.
- `POST /api/auth/email-session` verifies a Supabase session belongs to the requested email.
- `POST /api/auth/signup` creates the SpeedLink profile after Supabase email confirmation and password setup.
- `POST /api/auth/supabase` exchanges a Supabase session for a SpeedLink app token.
- `GET /api/auth/me` returns the saved profile for the bearer token.
- `PUT /api/auth/profile` updates saved profile details.
- `WS /ws?token=...` handles authenticated queueing, matching, accept/reject, call lifecycle, and WebRTC signaling.
