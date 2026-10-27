param([switch]$EnableDevTools)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Get-Content -LiteralPath (Join-Path $projectRoot '.env') | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
}
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
$env:DB_USER = $env:POSTGRES_USER
$env:DB_URL = "jdbc:postgresql://localhost:55432/$($env:POSTGRES_DB)"
$env:SPRING_PROFILES_ACTIVE = if ($EnableDevTools) { 'local' } else { 'production' }
$bundledMaven = Join-Path $projectRoot '.tools\apache-maven-3.9.11\bin\mvn.cmd'
$maven = if (Test-Path $bundledMaven) { $bundledMaven } else { 'mvn' }
Push-Location (Join-Path $projectRoot 'backend')
try { & $maven spring-boot:run; if ($LASTEXITCODE -ne 0) { throw 'Backend failed' } } finally { Pop-Location }
