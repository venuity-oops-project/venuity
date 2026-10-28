[CmdletBinding()]
param(
    [switch]$ForceRebuild,
    [switch]$SkipBuild,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$RemainingArgs
)

$ErrorActionPreference = 'Stop'
$jarPath = Join-Path $PSScriptRoot 'venuity-server\target\venuity.jar'
$rootJarPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'venuity.jar'
$envFile = Join-Path $PSScriptRoot '.env'
$exampleFile = Join-Path $PSScriptRoot '.env.example'

function Test-FileWriteable {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return $true }
    try {
        $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
        $stream.Close()
        $stream.Dispose()
        return $true
    } catch {
        return $false
    }
}

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

$port = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }

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

    # 5. Incremental build check: rebuild single JAR if missing, forced, or sources updated
    $jarExists = Test-Path -LiteralPath $jarPath
    $needsBuild = -not $jarExists -or $ForceRebuild

    if (-not $needsBuild -and -not $SkipBuild) {
        $sourcePaths = @(
            (Join-Path $PSScriptRoot 'venuity-server\src'),
            (Join-Path $PSScriptRoot 'venuity-common\src')
        )
        $pomPaths = @(
            (Join-Path $PSScriptRoot 'pom.xml'),
            (Join-Path $PSScriptRoot 'venuity-server\pom.xml'),
            (Join-Path $PSScriptRoot 'venuity-common\pom.xml')
        )

        $sourceFiles = foreach ($dir in $sourcePaths) {
            if (Test-Path -LiteralPath $dir) {
                Get-ChildItem -LiteralPath $dir -Recurse -File | Where-Object {
                    $_.Extension -in '.java', '.xml', '.yml', '.yaml', '.html', '.css', '.js', '.sql', '.properties'
                }
            }
        }
        $pomItems = foreach ($p in $pomPaths) {
            if (Test-Path -LiteralPath $p) { Get-Item -LiteralPath $p }
        }

        $allTracked = @($sourceFiles) + @($pomItems)
        $latestSource = $allTracked | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        $jarTime = (Get-Item -LiteralPath $jarPath).LastWriteTimeUtc

        if ($null -ne $latestSource -and $latestSource.LastWriteTimeUtc -gt $jarTime) {
            $needsBuild = $true
        }
    }

    if ($needsBuild -and -not $SkipBuild) {
        # Check if target JAR is locked before running Maven
        if ($jarExists -and -not (Test-FileWriteable -Path $jarPath)) {
            $listening = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($listening) {
                Write-Warning "Cannot rebuild 'venuity.jar' because it is locked by active process $($listening.OwningProcess) listening on port $port."
                Write-Host "[Venuity] An instance of Venuity is currently running at http://localhost:$port" -ForegroundColor Yellow
                Write-Host "[Venuity] To rebuild with latest changes, stop the running instance first (or kill process $($listening.OwningProcess)), then run again." -ForegroundColor Yellow
                Write-Host "[Venuity] Launching existing build without rebuilding..." -ForegroundColor Gray
                $needsBuild = $false
            } else {
                throw "Cannot rebuild 'venuity.jar': the file is locked by another running Java or IDE process. Please close the running application or process and retry."
            }
        }
    }

    if ($needsBuild -and -not $SkipBuild) {
        Write-Host "[Venuity] Building single executable JAR (venuity.jar) with Maven Wrapper..." -ForegroundColor Cyan
        & .\mvnw.cmd -B -ntp -pl venuity-server -am -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            Write-Host ""
            Write-Host "[Venuity ERROR] Maven packaging failed with exit code $LASTEXITCODE." -ForegroundColor Red
            Write-Host "Possible causes:" -ForegroundColor Yellow
            Write-Host "  1. Another process has an exclusive lock on 'venuity.jar'. Ensure no other Venuity process is running." -ForegroundColor Yellow
            Write-Host "  2. Compilation failure. Run '.\mvnw.cmd test-compile' to see detailed compile errors." -ForegroundColor Yellow
            throw "Maven packaging failed with exit code $LASTEXITCODE."
        }
    }

    # Synchronize single jar to root for top-level convenience
    if (Test-Path -LiteralPath $jarPath) {
        Copy-Item -LiteralPath $jarPath -Destination $rootJarPath -Force
    }

    # 6. Check if port is already listening before attempting to run
    $existingListener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($existingListener) {
        $existingProc = Get-Process -Id $existingListener.OwningProcess -ErrorAction SilentlyContinue
        Write-Host ""
        Write-Host "=================================================================" -ForegroundColor Yellow
        Write-Host "   VENUITY is ALREADY RUNNING on port $port" -ForegroundColor Yellow
        Write-Host "=================================================================" -ForegroundColor Yellow
        Write-Host "  -> Running Process ID: $($existingListener.OwningProcess) ($($existingProc.ProcessName))" -ForegroundColor Yellow
        Write-Host "  -> Application URL:    http://localhost:$port" -ForegroundColor Green
        Write-Host "  -> Swagger / OpenAPI:  http://localhost:$port/swagger-ui.html" -ForegroundColor Green
        Write-Host "  -> Health Endpoint:    http://localhost:$port/api/health" -ForegroundColor Green
        Write-Host "=================================================================" -ForegroundColor Yellow
        Write-Host "To restart, stop process $($existingListener.OwningProcess) first (or press Ctrl+C in its terminal), then re-run." -ForegroundColor Gray
        return
    }

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

    # 7. Run the single executable JAR
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
