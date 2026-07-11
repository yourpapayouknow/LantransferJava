$ErrorActionPreference = "Stop"

$dir = Join-Path $env:APPDATA "lantransfer"
$file = Join-Path $dir "acco"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

$token = Read-Host "classic PAT" -AsSecureString
if ($token.Length -eq 0) {
    throw "token empty"
}

$token | ConvertFrom-SecureString | Set-Content -LiteralPath $file -Encoding UTF8
Write-Host "ok"
