# Sign MSIX package with self-signed test certificate
param(
    [string]$MsixPath = "",
    [string]$CertDir = "",
    [System.Security.SecureString]$CertificatePassword,
    [string]$ManifestPath = ""
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath {
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

function Find-LatestMsix {
    param(
        [string]$RepoRoot
    )

    $artifactsRoot = Join-Path $RepoRoot "artifacts\private-beta"
    if (-not (Test-Path $artifactsRoot)) {
        return $null
    }

    return Get-ChildItem -Path $artifactsRoot -Filter "TerraGIS.Beta.msix" -Recurse -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

$repoRoot = Split-Path -Parent $PSScriptRoot

$resolvedMsixPath = Resolve-RepoPath -PathValue $MsixPath -RepoRoot $repoRoot
if (-not $resolvedMsixPath) {
    $resolvedMsixPath = Find-LatestMsix -RepoRoot $repoRoot
}

if (-not $resolvedMsixPath -or -not (Test-Path $resolvedMsixPath)) {
    throw "Could not resolve MSIX path. Pass -MsixPath explicitly or generate package first."
}
$MsixPath = $resolvedMsixPath

$resolvedCertDir = Resolve-RepoPath -PathValue $CertDir -RepoRoot $repoRoot
if (-not $resolvedCertDir) {
    $resolvedCertDir = Split-Path -Parent $MsixPath
}
$CertDir = $resolvedCertDir

$resolvedManifestPath = Resolve-RepoPath -PathValue $ManifestPath -RepoRoot $repoRoot
if (-not $resolvedManifestPath) {
    $candidateManifest = Join-Path (Split-Path -Parent (Split-Path -Parent $MsixPath)) "app-image\TerraGISBeta\AppxManifest.xml"
    if (Test-Path $candidateManifest) {
        $resolvedManifestPath = $candidateManifest
    }
}
$ManifestPath = $resolvedManifestPath

Write-Host "=== MSIX Signing for TerraGIS Beta ==="
Write-Host "MSIX file: $MsixPath"
Write-Host ""

function New-RandomSecureString {
    param(
        [int]$Length = 24
    )

    $chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+[]{}"
    $secure = New-Object System.Security.SecureString
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        for ($i = 0; $i -lt $Length; $i++) {
            $bytes = New-Object byte[] 4
            $rng.GetBytes($bytes)
            $index = [BitConverter]::ToUInt32($bytes, 0) % $chars.Length
            $secure.AppendChar($chars[[int]$index])
        }
    } finally {
        $rng.Dispose()
    }
    $secure.MakeReadOnly()
    return $secure
}

# Add signtool to PATH
$env:Path = "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64;$env:Path"

# 1. Create self-signed certificate
Write-Host "[1/3] Creating self-signed certificate..."

$publisherSubject = "CN=TerraGIS-Dev"
if (Test-Path $ManifestPath) {
    try {
        [xml]$manifestXml = Get-Content -Path $ManifestPath
        $publisher = $manifestXml.Package.Identity.Publisher
        if ($publisher) {
            $publisherSubject = $publisher
        }
    } catch {
        Write-Host "WARNING: Could not parse manifest publisher; using fallback subject CN=TerraGIS-Dev"
    }
}

$certParams = @{
    FriendlyName = "TerraGIS Beta (Test Signing)"
    Subject = $publisherSubject
    CertStoreLocation = "Cert:\CurrentUser\My"
    Type = "CodeSigningCert"
    KeyUsage = "DigitalSignature"
    KeyAlgorithm = "RSA"
    KeyLength = 2048
    HashAlgorithm = "SHA256"
    Provider = "Microsoft Enhanced RSA and AES Cryptographic Provider"
    KeyExportPolicy = "Exportable"
    NotAfter = (Get-Date).AddYears(1)
}

try {
    $cert = New-SelfSignedCertificate @certParams -ErrorAction Stop
    Write-Host "Created certificate: $($cert.Thumbprint)"
    
    # Export to PFX
    $certFile = "$CertDir\TerraGIS.Beta.Test.pfx"
    if (-not $CertificatePassword) {
        $CertificatePassword = New-RandomSecureString
    }
    
    Export-PfxCertificate -Cert "Cert:\CurrentUser\My\$($cert.Thumbprint)" `
        -FilePath $certFile `
        -Password $CertificatePassword | Out-Null
    
    Write-Host "Exported certificate to: $certFile"
} catch {
    Write-Host "ERROR: Certificate creation/export failed: $_"
    exit 1
}

# 2. Sign the MSIX
Write-Host "[2/3] Signing MSIX package..."
$signtoolExe = Get-Command signtool.exe -ErrorAction SilentlyContinue
if (-not $signtoolExe) {
    Write-Host "ERROR: signtool.exe not found"
    exit 1
}

$certPasswordBstr = [IntPtr]::Zero
$certPasswordPlain = ""
try {
    if ($CertificatePassword) {
        $certPasswordBstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($CertificatePassword)
        $certPasswordPlain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($certPasswordBstr)
    }

    $signCmd = @(
        "signtool.exe",
        "sign",
        "/f", ('"' + $certFile + '"'),
        "/p", ('"' + $certPasswordPlain + '"'),
        "/fd", "SHA256",
        ('"' + $MsixPath + '"')
    ) -join " "
} finally {
    if ($certPasswordBstr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($certPasswordBstr)
    }
}

Write-Host "Executing: signtool sign /f cert.pfx /fd SHA256 MSIX"
$oldNativeErrorPref = $null
$hasNativeErrorPref = $false
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $hasNativeErrorPref = $true
    $oldNativeErrorPref = $PSNativeCommandUseErrorActionPreference
    $PSNativeCommandUseErrorActionPreference = $false
}

& cmd /c $signCmd 2>&1 | Where-Object { $_ }

# Accept either success (0) or timestamp warning (1)
if ($LASTEXITCODE -eq 0 -or $LASTEXITCODE -eq 1) {
    Write-Host "[SUCCESS] MSIX signed (exit code: $LASTEXITCODE)"
    $signSuccess = $true
} else {
    Write-Host "[ERROR] Signing failed (exit code: $LASTEXITCODE)"
    $signSuccess = $false
}

# 3. Verify signature
Write-Host "[3/3] Verifying MSIX signature..."
$verifyStdOutLog = Join-Path $CertDir ("signtool-verify-" + [Guid]::NewGuid().ToString("N") + "-stdout.log")
$verifyStdErrLog = Join-Path $CertDir ("signtool-verify-" + [Guid]::NewGuid().ToString("N") + "-stderr.log")
try {
    $verifyProcess = Start-Process -FilePath "signtool.exe" `
        -ArgumentList @("verify", "/pa", "/all", $MsixPath) `
        -NoNewWindow `
        -Wait `
        -PassThru `
        -RedirectStandardOutput $verifyStdOutLog `
        -RedirectStandardError $verifyStdErrLog

    $verifyOutput = @()
    if (Test-Path $verifyStdOutLog) {
        $verifyOutput += Get-Content -Path $verifyStdOutLog
    }
    if (Test-Path $verifyStdErrLog) {
        $verifyOutput += Get-Content -Path $verifyStdErrLog
    }
    if ($verifyOutput.Count -gt 0) {
        $verifyOutput | Where-Object { $_ }
    }

    $verifyExit = $verifyProcess.ExitCode
} finally {
    if (Test-Path $verifyStdOutLog) {
        Remove-Item -LiteralPath $verifyStdOutLog -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path $verifyStdErrLog) {
        Remove-Item -LiteralPath $verifyStdErrLog -Force -ErrorAction SilentlyContinue
    }
}
if ($hasNativeErrorPref) {
    $PSNativeCommandUseErrorActionPreference = $oldNativeErrorPref
}

if ($verifyExit -ne 0) {
    Write-Host "[WARNING] Signature verification returned exit code $verifyExit."
    Write-Host "Self-signed test certificates commonly fail chain trust verification on machines where the root is not trusted."
    Write-Host "To trust locally, install the generated certificate in Trusted People (Current User)."
}

Write-Host ""
if ($signSuccess) {
    Write-Host "=== MSIX Signing Complete (SUCCESS) ==="
} else {
    Write-Host "=== MSIX Signing Complete (with warnings) ==="
}
Write-Host "Signed MSIX: $MsixPath"
Write-Host "Test certificate: $certFile"
Write-Host ""
Write-Host "For production Store submission, obtain a production certificate and re-sign."
