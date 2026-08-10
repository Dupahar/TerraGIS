param(
    [string]$MsixPath = "",
    [string]$CertificatePath = "",
    [ValidateSet("CurrentUser", "LocalMachine")]
    [string]$Scope = "CurrentUser",
    [switch]$Install,
    [switch]$SkipSigntool
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-RepoRoot {
    return Split-Path -Parent $PSScriptRoot
}

function Resolve-InputPath {
    param(
        [string]$PathValue,
        [string]$RepoRoot
    )

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $null
    }

    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }

    return Join-Path $RepoRoot $PathValue
}

function Get-LatestMsixPath {
    param(
        [string]$RepoRoot
    )

    $artifactsRoot = Join-Path $RepoRoot "artifacts\private-beta"
    if (-not (Test-Path $artifactsRoot)) {
        return $null
    }

    $latestMsix = Get-ChildItem -Path $artifactsRoot -Filter "TerraGIS.Beta.msix" -Recurse -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $latestMsix) {
        return $null
    }

    return $latestMsix.FullName
}

function Test-IsAdmin {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-CertStorePrefix {
    param(
        [string]$TargetScope
    )

    if ($TargetScope -eq "LocalMachine") {
        return "Cert:\LocalMachine"
    }

    return "Cert:\CurrentUser"
}

$repoRoot = Resolve-RepoRoot

$resolvedMsixPath = Resolve-InputPath -PathValue $MsixPath -RepoRoot $repoRoot
if (-not $resolvedMsixPath) {
    $resolvedMsixPath = Get-LatestMsixPath -RepoRoot $repoRoot
}

if (-not $resolvedMsixPath -or -not (Test-Path $resolvedMsixPath)) {
    throw "MSIX not found. Pass -MsixPath explicitly."
}

$resolvedCertificatePath = Resolve-InputPath -PathValue $CertificatePath -RepoRoot $repoRoot
if (-not $resolvedCertificatePath) {
    $resolvedCertificatePath = Join-Path (Split-Path -Parent $resolvedMsixPath) "TerraGIS.Beta.Test.cer"
}

if (-not (Test-Path $resolvedCertificatePath)) {
    throw "Certificate not found: $resolvedCertificatePath"
}

if ($Scope -eq "LocalMachine" -and -not (Test-IsAdmin)) {
    throw "LocalMachine scope requires an elevated PowerShell session (Run as Administrator)."
}

$signature = Get-AuthenticodeSignature -FilePath $resolvedMsixPath
if ($null -eq $signature.SignerCertificate) {
    throw "The MSIX is not signed: $resolvedMsixPath"
}

$thumbprint = $signature.SignerCertificate.Thumbprint
$storePrefix = Get-CertStorePrefix -TargetScope $Scope
$trustedPeopleStore = Join-Path $storePrefix "TrustedPeople"
$rootStore = Join-Path $storePrefix "Root"

Write-Host "MSIX: $resolvedMsixPath"
Write-Host "CERT: $resolvedCertificatePath"
Write-Host "SCOPE: $Scope"
Write-Host "THUMBPRINT: $thumbprint"

Import-Certificate -FilePath $resolvedCertificatePath -CertStoreLocation $trustedPeopleStore | Out-Null
Import-Certificate -FilePath $resolvedCertificatePath -CertStoreLocation $rootStore | Out-Null

$trustedPeoplePresent = $null -ne (Get-ChildItem $trustedPeopleStore | Where-Object Thumbprint -eq $thumbprint | Select-Object -First 1)
$rootPresent = $null -ne (Get-ChildItem $rootStore | Where-Object Thumbprint -eq $thumbprint | Select-Object -First 1)

if (-not $trustedPeoplePresent -or -not $rootPresent) {
    throw "Certificate import verification failed. TrustedPeople=$trustedPeoplePresent Root=$rootPresent"
}

Write-Host "CERT_TRUST_OK: TrustedPeople=$trustedPeoplePresent Root=$rootPresent"

$signatureAfterImport = Get-AuthenticodeSignature -FilePath $resolvedMsixPath
Write-Host "AUTHENTICODE_STATUS: $($signatureAfterImport.Status)"

if (-not $SkipSigntool) {
    $signtool = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($signtool) {
        & $signtool.Source verify /pa /all "$resolvedMsixPath"
        if ($LASTEXITCODE -ne 0) {
            throw "signtool verification failed with exit code $LASTEXITCODE"
        }
        Write-Host "SIGNTOOL_VERIFY_OK"
    } else {
        Write-Warning "signtool.exe not found on PATH; skipped signtool verification."
    }
}

if ($Install) {
    Add-AppxPackage -Path $resolvedMsixPath -ForceUpdateFromAnyVersion -ErrorAction Stop
    Write-Host "INSTALL_OK"
} else {
    Write-Host "Install skipped. Use -Install to install this MSIX after trust setup."
}
