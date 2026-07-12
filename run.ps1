$ErrorActionPreference = "Stop"

Set-Location -LiteralPath $PSScriptRoot

$file = Join-Path (Join-Path $env:APPDATA "lantransfer") "acco"
if (!(Test-Path -LiteralPath $file)) {
    throw "run .\tok.ps1 first"
}

try {
    $env:ACCO_T = (Get-Content -LiteralPath $file -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($env:ACCO_T)) {
        throw "token empty"
    }
    if (!$env:ACCO_T.StartsWith("ghp_")) {
        throw "run .\tok.ps1 with classic PAT"
    }
    & mvn -q javafx:run
} finally {
    Remove-Item Env:\ACCO_T -ErrorAction SilentlyContinue
}
