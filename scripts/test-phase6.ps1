param(
    [string]$TestApi = 'http://127.0.0.1:8080',
    [switch]$IncludeDungeon,
    [switch]$IncludeLoad
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
$previousApi = $env:TEST_API
try {
    if ($IncludeLoad -and $TestApi -ne 'http://127.0.0.1:8080') {
        throw 'Load CPU/RAM được đo ở Docker backend 8080; chạy IncludeLoad với TestApi mặc định.'
    }
    $env:TEST_API = $TestApi
    & node --test tests/network-client.test.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Frontend transport tests failed' }
    foreach ($testName in @('reconnect', 'party', 'duel')) {
        & node "tests/$testName.mjs"
        if ($LASTEXITCODE -ne 0) { throw "Phase 6 $testName integration failed" }
    }
    if ($IncludeDungeon) {
        Get-Content -LiteralPath '.env' | ForEach-Object {
            if ($_ -match '^DEV_ADMIN_KEY=(.*)$') { $env:DEV_ADMIN_KEY = $matches[1] }
        }
        Write-Host 'Dungeon yêu cầu backend local profile ở TestApi đã chọn; không bật dev trong Docker production.'
        & node tests/dungeon.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Dungeon integration failed' }
    }
    if ($IncludeLoad) {
        # The integration cases above create ten accounts; respect the one-minute register window.
        Write-Host 'Đợi cửa sổ rate limit trước lượt tải (60 giây).'
        Start-Sleep -Seconds 60
        & node tests/load.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Five-minute load acceptance failed; inspect .runtime/phase6-load.json and backend logs' }
    }
} finally {
    $env:TEST_API = $previousApi
    Pop-Location
}
