# Direct MSIX packaging wrapper.
# Keeps package-only flow aligned with create-and-sign-msix.ps1 behavior.
param(
    [string]$AppImagePath = "",
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$createAndSignScript = Join-Path $scriptRoot "create-and-sign-msix.ps1"

if (-not (Test-Path $createAndSignScript)) {
    throw "Required script not found: $createAndSignScript"
}

$invokeArgs = @{
    SkipSign = $true
}

if (-not [string]::IsNullOrWhiteSpace($AppImagePath)) {
    $invokeArgs.AppImagePath = $AppImagePath
}

if (-not [string]::IsNullOrWhiteSpace($OutputDir)) {
    $invokeArgs.OutputDir = $OutputDir
}

Write-Host "=== MSIX Packaging for TerraGIS Beta ==="
Write-Host "Delegating to create-and-sign-msix.ps1 in package-only mode"

& $createAndSignScript @invokeArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
