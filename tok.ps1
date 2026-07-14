$ErrorActionPreference = "Stop"

$dir = Join-Path $env:APPDATA "lantransfer"
$file = Join-Path $dir "acco"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

$token = (Read-Host "请输入您的 VIP 注册授权 Token (以 ghp_ 开头)").Trim()
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "token empty"
}
if (!$token.StartsWith("ghp_")) {
    throw "VIP 注册授权 Token 格式不正确，必须以 ghp_ 开头"
}

$token | Set-Content -LiteralPath $file -Encoding UTF8
Write-Host "ok"
