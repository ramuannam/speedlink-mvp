$envFile = Join-Path $PSScriptRoot "..\backend\.env.local"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $parts = $line.Split("=", 2)
        if ($parts.Count -ne 2) {
            return
        }

        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($name -and $name -ne "SPRING_PROFILES_ACTIVE") {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
if (-not $env:SPEEDLINK_JWT_SECRET) { $env:SPEEDLINK_JWT_SECRET = "local-dev-change-me-replace-this" }
if (-not $env:SPEEDLINK_TOKEN_TTL_MINUTES) { $env:SPEEDLINK_TOKEN_TTL_MINUTES = "720" }
if (-not $env:SPEEDLINK_CORS_ALLOWED_ORIGINS) { $env:SPEEDLINK_CORS_ALLOWED_ORIGINS = "http://localhost:5173,http://127.0.0.1:5173" }

Set-Location "$PSScriptRoot\..\backend"
.\mvnw.cmd spring-boot:run
