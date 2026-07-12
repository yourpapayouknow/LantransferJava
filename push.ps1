param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$m
)

$ErrorActionPreference = "Stop"

# Commit message
$msg = ($m -join " ").Trim()
if ([string]::IsNullOrWhiteSpace($msg)) {
    $msg = "chore: update " + (Get-Date -Format "yyyyMMdd-HHmmss")
}

# Current branch
$branch = (git branch --show-current).Trim()
if ([string]::IsNullOrWhiteSpace($branch)) {
    throw "No current branch"
}

# Stage all files
git add -A

# Commit current changes
if ((git status --porcelain).Trim()) {
    git commit -m $msg
} else {
    Write-Host "No changes to commit"
}

# Push current branch
git push origin $branch
