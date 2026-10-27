param([switch]$IncludeProgression)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    & node tests/multiplayer.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Multiplayer integration test failed' }
    & node tests/combat.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Combat integration test failed' }
    if ($IncludeProgression) {
        Get-Content -LiteralPath '.env' | ForEach-Object {
            if ($_ -match '^(DEV_ADMIN_KEY)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
        }
        & node tests/progression.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Progression integration test failed' }
    }
} finally { Pop-Location }
