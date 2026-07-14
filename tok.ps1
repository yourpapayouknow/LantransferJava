$ErrorActionPreference = "Stop"

$dir = Join-Path $env:APPDATA "lantransfer"
$file = Join-Path $dir "acco"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

$token = (Read-Host "classic PAT").Trim()
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "token empty"
}
if (!$token.StartsWith("ghp_")) {
    throw "classic PAT must start with ghp_"
}

$token | Set-Content -LiteralPath $file -Encoding UTF8
Write-Host "ok"
