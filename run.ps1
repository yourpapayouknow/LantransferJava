$ErrorActionPreference = "Stop"

Set-Location -LiteralPath $PSScriptRoot

$file = Join-Path (Join-Path $env:APPDATA "lantransfer") "acco"
if (!(Test-Path -LiteralPath $file)) {
    throw "run .\tok.ps1 first"
}

$secure = (Get-Content -LiteralPath $file -Raw).Trim() | ConvertTo-SecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:ACCO_T = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    if ([string]::IsNullOrWhiteSpace($env:ACCO_T)) {
        throw "token empty"
    }
    if (!$env:ACCO_T.StartsWith("ghp_")) {
        throw "run .\tok.ps1 with classic PAT"
    }
    & mvn -q javafx:run
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    Remove-Item Env:\ACCO_T -ErrorAction SilentlyContinue
}
