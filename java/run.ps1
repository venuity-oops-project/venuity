[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$RemainingArgs
)

$ErrorActionPreference = 'Stop'
$jarPath = Join-Path $PSScriptRoot 'venuity-server\target\venuity.jar'
$rootJarPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'venuity.jar'
$envFile = Join-Path $PSScriptRoot '.env'
$exampleFile = Join-Path $PSScriptRoot '.env.example'

# 1. Ensure .env exists with ready-to-run local defaults if not already present
if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Host "[Venuity] Initializing $envFile from .env.example with secure local defaults..." -ForegroundColor Cyan
    if (Test-Path -LiteralPath $exampleFile) {
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        $bytes = New-Object byte[] 32
        $rng.GetBytes($bytes)
        $randomSecret = [Convert]::ToBase64String($bytes)
        
        $envContent = Get-Content -LiteralPath $exampleFile -Raw
        $envContent = $envContent -replace 'replace-with-strong-root-password', 'local-root-only-9f3a'
        $envContent = $envContent -replace 'replace-with-strong-app-password', 'local-app-only-7b2c'
        $envContent = $envContent -replace 'replace-with-at-least-32-random-bytes', $randomSecret
        $envContent = $envContent -replace 'replace-with-a-strong-admin-password', 'admin'
        Set-Content -LiteralPath $envFile -Value $envContent -Encoding utf8
    } else {
        throw "Missing $exampleFile. Cannot initialize environment configuration."
    }
}

# 2. Load environment variables from .env
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$' -and -not $_.TrimStart().StartsWith('#')) {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}

# Ensure dev JWT_SECRET fallback if missing in .env
if (-not $env:JWT_SECRET -or $env:JWT_SECRET -eq 'replace-with-at-least-32-random-bytes') {
    $env:JWT_SECRET = 'dev-only-venuity-secret-change-me-32-bytes'
}

# 3. Check for Java 25 runtime
if ((Get-Command java -ErrorAction SilentlyContinue) -eq $null) {
    throw 'Java 25 is required but the java command was not found on PATH. Please install Eclipse Temurin OpenJDK 25.'
}

$javaVersion = (cmd /c "java -version 2>&1" | Out-String)
if ($javaVersion -notmatch 'version "25(?:\.|\")') {
    throw "Java 25 is required. Detected: $javaVersion. Please install or switch to Eclipse Temurin OpenJDK 25."
}

Push-Location $PSScriptRoot
try {
    # 4. Ensure MySQL database is up
    if ((Get-Command docker -ErrorAction SilentlyContinue) -ne $null) {
        Write-Host "[Venuity] Starting / checking MySQL database container..." -ForegroundColor Cyan
        & docker compose up -d --wait mysql
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Docker compose returned non-zero exit code ($LASTEXITCODE). Proceeding in case MySQL is already running on port 3306."
        }
    } else {
        Write-Warning "Docker command not found on PATH. Assuming MySQL is running locally on port 3306."
    }

    # 5. Incremental build check: rebuild single JAR if missing or sources updated
    $needsBuild = -not (Test-Path -LiteralPath $jarPath)
    if (-not $needsBuild) {
        $latestSource = Get-ChildItem -Recurse -File |
            Where-Object { $_.FullName -notmatch '\\target\\' -and $_.Extension -in '.java', '.xml', '.yml', '.yaml', '.html', '.css', '.js' } |
            Sort-Object LastWriteTimeUtc -Descending |
            Select-Object -First 1
        $needsBuild = $null -ne $latestSource -and $latestSource.LastWriteTimeUtc -gt (Get-Item -LiteralPath $jarPath).LastWriteTimeUtc
    }

    if ($needsBuild) {
        Write-Host "[Venuity] Building single executable JAR (venuity.jar) with Maven Wrapper..." -ForegroundColor Cyan
        & .\mvnw.cmd -B -ntp -pl venuity-server -am -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Maven packaging failed with exit code $LASTEXITCODE." }
    }

    # Synchronize single jar to root for top-level convenience
    if (Test-Path -LiteralPath $jarPath) {
        Copy-Item -LiteralPath $jarPath -Destination $rootJarPath -Force
    }

    $port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8080" }
    Write-Host ""
    Write-Host "=================================================================" -ForegroundColor Green
    Write-Host "   VENUITY - PlayArea Manager (Java 25 Single-JAR Application)   " -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Green
    Write-Host "  -> Application URL:   http://localhost:$port" -ForegroundColor Yellow
    Write-Host "  -> Swagger / OpenAPI: http://localhost:$port/swagger-ui.html" -ForegroundColor Yellow
    Write-Host "  -> Health Endpoint:   http://localhost:$port/api/health" -ForegroundColor Yellow
    Write-Host "  -> Default Admin:     admin / admin" -ForegroundColor Yellow
    Write-Host "  -> Single JAR File:   $jarPath" -ForegroundColor Gray
    Write-Host "=================================================================" -ForegroundColor Green
    Write-Host "Press Ctrl+C to stop the application." -ForegroundColor Gray
    Write-Host ""

    # 6. Run the single executable JAR
    if ($RemainingArgs) {
        & java -jar $jarPath @RemainingArgs
    } else {
        & java -jar $jarPath
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}
