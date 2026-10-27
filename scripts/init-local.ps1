$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envPath = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envPath) { Write-Host '.env already exists; keeping existing secrets.'; exit 0 }
function New-LocalSecret {
    $bytes = New-Object byte[] 48
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
@(
    'POSTGRES_DB=tamtoc'
    'POSTGRES_USER=tamtoc'
    "POSTGRES_PASSWORD=$(New-LocalSecret)"
    "JWT_SECRET=$(New-LocalSecret)"
    "DEV_ADMIN_KEY=$(New-LocalSecret)"
    'ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173'
) | Set-Content -LiteralPath $envPath -Encoding utf8
Write-Host 'Created .env with random local secrets. Do not commit this file.'
