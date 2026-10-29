param([switch]$IncludeProgression)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $world15Tests = @('multiplayer.mjs', 'maps.mjs', 'bots.mjs', 'duel.mjs')
    foreach ($testName in $world15Tests) {
        & node (Join-Path 'tests' $testName)
        if ($LASTEXITCODE -ne 0) { throw "Integration test failed: $testName" }
    }
    if ($IncludeProgression) {
        Get-Content -LiteralPath '.env' | ForEach-Object {
            if ($_ -match '^(DEV_ADMIN_KEY)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
        }
        foreach ($testName in @('world15-routes.mjs', 'world15-catalog.mjs', 'dungeon.mjs', 'world15-dungeon.mjs', 'world15-archived-save.mjs')) {
            & node (Join-Path 'tests' $testName)
            if ($LASTEXITCODE -ne 0) { throw "World15 progression test failed: $testName" }
        }
    }
} finally { Pop-Location }
