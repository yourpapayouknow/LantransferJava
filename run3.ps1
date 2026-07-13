$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$tokenFile = Join-Path (Join-Path $env:APPDATA "lantransfer") "acco"
if (!(Test-Path -LiteralPath $tokenFile)) {
    throw "run .\tok.ps1 first"
}

$base = Join-Path $PSScriptRoot ".multi"
$ports = 1..3 | ForEach-Object { 49129 + ($_ * 2) }
$scanPorts = $ports -join ","

Get-CimInstance Win32_Process |
    Where-Object {
        $_.CommandLine -like "*$PSScriptRoot*" -and
        ($_.CommandLine -like "*javafx:run*" -or $_.CommandLine -like "*com.iwmei.lantransfer.App*")
    } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

function Q($text) {
    return $text.Replace("'", "''")
}

for ($i = 1; $i -le 3; $i++) {
    $dir = Join-Path $base "p$i"
    $findPort = $ports[$i - 1]
    $sendPort = $findPort + 1
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $cmd = @"
`$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath '$(Q $PSScriptRoot)'
`$env:ACCO_T = (Get-Content -LiteralPath '$(Q $tokenFile)' -Raw).Trim()
`$env:JAVA_TOOL_OPTIONS = '-Dlantransfer.dataDir=$(Q $dir) -Dlantransfer.discoveryPort=$findPort -Dlantransfer.discoveryPorts=$scanPorts -Dlantransfer.transferPort=$sendPort'
`$Host.UI.RawUI.WindowTitle = 'LanTransfer-$i'
& mvn -q javafx:run
"@
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($cmd))
    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-EncodedCommand", $encoded
    Start-Sleep -Milliseconds 600
}

1..3 | ForEach-Object {
    $findPort = $ports[$_ - 1]
    [pscustomobject]@{
        Id = $_
        Data = Join-Path $base "p$_"
        Discovery = $findPort
        Transfer = $findPort + 1
    }
} | Format-Table -AutoSize
