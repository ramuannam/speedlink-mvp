# SpeedLink Project Initiation Commands

This file is the local development startup checklist for SpeedLink MVP.

Use these commands when you want to test locally before pushing to production.

## 1. Open Project Root

```powershell
cd D:\SpeedLink\speedlink-mvp
```

## 2. Start Docker Desktop

Make sure Docker Desktop is running before starting Postgres and Redis.

If Docker is not running, this command may fail with a Docker pipe/engine error.

## 3. Start Local Postgres and Redis

```powershell
docker-compose up -d postgres redis
```

This starts only the local database and Redis services.

It does not push anything to production.

## 4. Start Backend in Local Mode

From the project root:

```powershell
.\scripts\start-backend-local.ps1
```

Use this instead of plain `mvn spring-boot:run`.

The script loads `backend/.env.local`, sets the `local` Spring profile, and makes sure the backend uses your local Postgres, local Redis, and dev Supabase config.

Plain `mvn spring-boot:run` does not automatically load `backend/.env.local`, so it can miss important local values.

## 5. Start Frontend

Open a new PowerShell window:

```powershell
cd D:\SpeedLink\speedlink-mvp\frontend
npm run dev
```

Then open:

```text
http://localhost:5173
```

## 6. Verify Backend Is Running

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Expected result:

```json
{
  "status": "ok"
}
```

## 7. Verify Backend Uses Dev Supabase

```powershell
Invoke-RestMethod http://localhost:8080/api/auth/supabase-config
```

Expected project ref for current dev setup:

```text
nqetickfrncjftvyiaca
```

If this does not match your frontend `VITE_SUPABASE_URL`, auth will be blocked by the app.

That is intentional. It prevents local testing from accidentally creating users in production Supabase.

## 8. Check Running Ports

Use this when the app behaves strangely or you suspect an old backend/frontend is still running.

```powershell
netstat -ano | findstr :8080
netstat -ano | findstr :5173
```

If an old process is using a port, stop it:

```powershell
Stop-Process -Id YOUR_PID -Force
```

Then restart backend/frontend normally.

## 9. Local Environment Files

Backend local env:

```text
D:\SpeedLink\speedlink-mvp\backend\.env.local
```

Frontend local env:

```text
D:\SpeedLink\speedlink-mvp\frontend\.env.local
```

Frontend Supabase variables must use `VITE_` prefix:

```env
VITE_SUPABASE_URL=https://your-dev-project.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=your-dev-publishable-key
```

Backend Supabase variables do not use `VITE_`:

```env
SUPABASE_URL=https://your-dev-project.supabase.co
SUPABASE_PUBLISHABLE_KEY=your-dev-publishable-key
SUPABASE_SERVICE_ROLE_KEY=your-dev-service-role-key
```

Never put prod Supabase keys in local dev files.

## 10. Stop Local Services

Stop containers but keep local DB data:

```powershell
docker-compose down
```

Stop containers and delete local DB data:

```powershell
docker-compose down -v
```

Use `down -v` only when you intentionally want a fresh local database.

## 11. Common Fixes

If backend cannot connect to Postgres, make sure Docker Postgres is running:

```powershell
docker-compose ps
```

If Windows native PostgreSQL is occupying port `5432`, check:

```powershell
netstat -ano | findstr :5432
```

If needed, stop the Windows PostgreSQL service from an Administrator PowerShell:

```powershell
Stop-Service postgresql-x64-17
Set-Service postgresql-x64-17 -StartupType Manual
```

The local backend startup script forces Java and Postgres to use `Asia/Kolkata`.

If backend still shows timezone error for `Asia/Calcutta`, restart the PowerShell window and run:

```powershell
cd D:\SpeedLink\speedlink-mvp
.\scripts\start-backend-local.ps1
```

If it still fails, set these once in that PowerShell window and restart backend:

```powershell
$env:JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Kolkata"
$env:MAVEN_OPTS="-Duser.timezone=Asia/Kolkata"
.\scripts\start-backend-local.ps1
```

## 12. Recommended Daily Flow

```powershell
cd D:\SpeedLink\speedlink-mvp
docker-compose up -d postgres redis
.\scripts\start-backend-local.ps1
```

In another PowerShell:

```powershell
cd D:\SpeedLink\speedlink-mvp\frontend
npm run dev
```

Open:

```text
http://localhost:5173
```
