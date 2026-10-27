$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Load environment variables from .env if present
$envFile = Join-Path $rootDir ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line -split "=", 2
            if ($parts.Length -eq 2) {
                $key = $parts[0].Trim()
                $val = $parts[1].Trim()
                if (-not [Environment]::GetEnvironmentVariable($key)) {
                    [Environment]::SetEnvironmentVariable($key, $val, "Process")
                }
            }
        }
    }
}

$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Users\gaura\.jdks\temurin-21.0.11" }
$mvnDir = "C:\Users\gaura\.m2\wrapper\dists\apache-maven-3.9.9-bin\4nf9hui3q3djbarqar9g711ggc\apache-maven-3.9.9\bin"
$env:PATH = "$env:JAVA_HOME\bin;$mvnDir;$env:PATH"

if (-not $env:DB_HOST) { $env:DB_HOST = "127.0.0.1" }
if (-not $env:DB_PORT) { $env:DB_PORT = "3006" }
if (-not $env:DB_NAME) { $env:DB_NAME = "interview_simulator" }
if (-not $env:DB_USER) { $env:DB_USER = "interview_app" }
if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = "interview_secret_2026" }
if (-not $env:RUNNER_SERVICE_URL) { $env:RUNNER_SERVICE_URL = "http://127.0.0.1:8085" }
if (-not $env:AI_PROVIDER) { $env:AI_PROVIDER = "gemini" }
if (-not $env:AI_BASE_URL) { $env:AI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai" }
if (-not $env:AI_MODEL) { $env:AI_MODEL = "gemini-2.5-flash" }
if (-not $env:AI_TIMEOUT_MS) { $env:AI_TIMEOUT_MS = "15000" }

$javaExe = "$env:JAVA_HOME\bin\java.exe"
$mvnCmd = "$mvnDir\mvn.cmd"

if (-not (Test-Path "$rootDir\runner-service\target\runner-service-1.0.0.jar")) {
    Write-Host "Building runner-service JAR..." -ForegroundColor Cyan
    Push-Location "$rootDir\runner-service"
    & $mvnCmd package -DskipTests
    Pop-Location
}

if (-not (Test-Path "$rootDir\backend\target\interview-simulator-backend-1.0.0.jar")) {
    Write-Host "Building backend JAR..." -ForegroundColor Cyan
    Push-Location "$rootDir\backend"
    & $mvnCmd package -DskipTests
    Pop-Location
}

Write-Host "Starting Isolated Code Execution Service (runner-service) on http://localhost:8085 ..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:JAVA_HOME='$env:JAVA_HOME'; & '$javaExe' -jar '$rootDir\runner-service\target\runner-service-1.0.0.jar'"

Write-Host "Starting Spring Boot Backend API on http://localhost:8080 (MySQL 8 on port $env:DB_PORT) ..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$rootDir'; `$env:JAVA_HOME='$env:JAVA_HOME'; `$env:DB_HOST='$env:DB_HOST'; `$env:DB_PORT='$env:DB_PORT'; `$env:DB_NAME='$env:DB_NAME'; `$env:DB_USER='$env:DB_USER'; `$env:DB_PASSWORD='$env:DB_PASSWORD'; `$env:RUNNER_SERVICE_URL='$env:RUNNER_SERVICE_URL'; `$env:AI_PROVIDER='$env:AI_PROVIDER'; `$env:AI_BASE_URL='$env:AI_BASE_URL'; `$env:AI_MODEL='$env:AI_MODEL'; `$env:AI_API_KEY='$env:AI_API_KEY'; `$env:AI_TIMEOUT_MS='$env:AI_TIMEOUT_MS'; & '$javaExe' -jar '$rootDir\backend\target\interview-simulator-backend-1.0.0.jar'"

Write-Host "Starting React + Vite Frontend on http://localhost:5173 ..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$rootDir\frontend'; npm run dev"

Write-Host ""
Write-Host "All 3 services launched in separate PowerShell windows:" -ForegroundColor Cyan
Write-Host "  - Frontend UI:      http://localhost:5173"
Write-Host "  - Backend API:      http://localhost:8080"
Write-Host "  - Swagger OpenAPI:  http://localhost:8080/swagger-ui.html"
Write-Host "  - Code Runner:      http://localhost:8085/api/runner/health"
