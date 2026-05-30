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
        if ($name) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

$env:SPRING_PROFILES_ACTIVE = "local"
if (-not $env:DB_HOST -or $env:DB_HOST -eq "localhost") { $env:DB_HOST = "127.0.0.1" }
if (-not $env:DB_PORT) { $env:DB_PORT = "5432" }
if (-not $env:DB_NAME) { $env:DB_NAME = "speedlink" }
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = "speedlink" }
if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = "password" }
if (-not $env:REDIS_HOST) { $env:REDIS_HOST = "localhost" }
if (-not $env:REDIS_PORT) { $env:REDIS_PORT = "6379" }
if (-not $env:SPEEDLINK_JWT_SECRET) { $env:SPEEDLINK_JWT_SECRET = "local-dev-change-me-replace-this" }
if (-not $env:SPEEDLINK_TOKEN_TTL_MINUTES) { $env:SPEEDLINK_TOKEN_TTL_MINUTES = "720" }
if (-not $env:SPEEDLINK_CORS_ALLOWED_ORIGINS) { $env:SPEEDLINK_CORS_ALLOWED_ORIGINS = "http://localhost:5173,http://127.0.0.1:5173" }
if (-not $env:TZ -or $env:TZ -eq "Asia/Calcutta") { $env:TZ = "Asia/Kolkata" }
if (-not $env:PGTZ -or $env:PGTZ -eq "Asia/Calcutta") { $env:PGTZ = "Asia/Kolkata" }

if ($env:JAVA_TOOL_OPTIONS -match "Asia/Calcutta") {
    $env:JAVA_TOOL_OPTIONS = $env:JAVA_TOOL_OPTIONS -replace "Asia/Calcutta", "Asia/Kolkata"
}
if ($env:MAVEN_OPTS -match "Asia/Calcutta") {
    $env:MAVEN_OPTS = $env:MAVEN_OPTS -replace "Asia/Calcutta", "Asia/Kolkata"
}
if (-not $env:JAVA_TOOL_OPTIONS -or $env:JAVA_TOOL_OPTIONS -notmatch "user\.timezone") {
    $env:JAVA_TOOL_OPTIONS = (($env:JAVA_TOOL_OPTIONS, "-Duser.timezone=Asia/Kolkata") -ne "" -join " ").Trim()
}
if (-not $env:MAVEN_OPTS -or $env:MAVEN_OPTS -notmatch "user\.timezone") {
    $env:MAVEN_OPTS = (($env:MAVEN_OPTS, "-Duser.timezone=Asia/Kolkata") -ne "" -join " ").Trim()
}

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://$($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)?options=-c%20TimeZone=Asia/Kolkata"
$env:SPRING_DATASOURCE_USERNAME = $env:DB_USERNAME
$env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD
$env:SPRING_DATASOURCE_DRIVER = "org.postgresql.Driver"

Set-Location "$PSScriptRoot\..\backend"
if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -eq 0) {
        exit 0
    }
    Write-Warning "Maven wrapper failed, falling back to installed mvn.cmd."
}

mvn.cmd spring-boot:run
