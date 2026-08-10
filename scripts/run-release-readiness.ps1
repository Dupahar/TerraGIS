param(
    [string]$JavaHome = "C:\Program Files\Java\jdk-25.0.2",
    [string]$VersionTag = "1.0.0-beta.6",
    [switch]$SkipPackaging
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Test-Path $JavaHome)) {
    throw "JAVA_HOME not found: $JavaHome"
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

Write-Host "==> Release readiness preflight"
Write-Host "Repo: $repoRoot"
Write-Host "Version: $VersionTag"
Write-Host "JAVA_HOME: $env:JAVA_HOME"

Write-Host ""
Write-Host "==> Step 1/4: Full quality gate"
& .\mvnw.cmd clean verify
if ($LASTEXITCODE -ne 0) {
    throw "mvnw clean verify failed"
}

Write-Host ""
Write-Host "==> Step 2/4: Store toolchain checks"
$makeAppx = Get-Command makeappx.exe -ErrorAction SilentlyContinue
$signtool = Get-Command signtool.exe -ErrorAction SilentlyContinue
Write-Host ("makeappx.exe available: {0}" -f [bool]$makeAppx)
Write-Host ("signtool.exe available: {0}" -f [bool]$signtool)

Write-Host ""
Write-Host "==> Step 3/4: Package private beta"
if ($SkipPackaging) {
    Write-Host "Skipped packaging because -SkipPackaging was set"
} else {
    & .\scripts\prepare-private-beta.ps1 -JavaHome $JavaHome -VersionTag $VersionTag
    if ($LASTEXITCODE -ne 0) {
        throw "prepare-private-beta.ps1 failed"
    }
}

Write-Host ""
Write-Host "==> Step 4/4: Clean-machine validation reminder"
Write-Host "Use this template after each machine run:"
Write-Host ".\docs\release_evidence\clean_machine_validation_template_1.0.0-beta.6.md"
Write-Host ""
Write-Host "Release readiness flow completed."
