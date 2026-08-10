param(
    [string]$AppImagePath = "",
    [string]$OutputDir = "",
    [string]$LogoSourcePath = "",
    [string]$CertificatePath = "",
    [System.Security.SecureString]$CertificatePassword,
    [string]$PackageVersion = "",
    [ValidateSet("x86", "x64", "arm", "arm64", "neutral")]
    [string]$ProcessorArchitecture = "x64",
    [string]$IdentityName = "Dupahar.TerraGIS",
    [string]$IdentityPublisher = "CN=E8B9DFF8-00AF-4BB0-A12B-23EBCE61FEE8",
    [string]$PackageDisplayName = "TerraGIS",
    [string]$PublisherDisplayName = "Dupahar",
    [switch]$TestSign,
    [switch]$SkipSign,
    [switch]$RequireSignature
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

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

function Find-LatestAppImagePath {
    param(
        [string]$RepoRoot
    )

    $artifactsRoot = Join-Path $RepoRoot "artifacts\private-beta"
    if (-not (Test-Path $artifactsRoot)) {
        return $null
    }

    $candidateRuns = Get-ChildItem -Path $artifactsRoot -Directory -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending

    foreach ($run in $candidateRuns) {
        $candidate = Join-Path $run.FullName "app-image\TerraGISBeta"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    return $null
}

function Resolve-AppExecutableName {
    param(
        [string]$ResolvedAppImagePath
    )

    $candidates = Get-ChildItem -Path $ResolvedAppImagePath -Filter "*.exe" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '^(?i:unins|setup)' -and $_.Name -notmatch '(?i:updater)' }

    if (-not $candidates) {
        throw "No executable was found at app-image root: $ResolvedAppImagePath"
    }

    $preferred = $candidates | Where-Object { $_.Name -ieq "TerraGISBeta.exe" } | Select-Object -First 1
    if ($preferred) {
        return $preferred.Name
    }

    return ($candidates | Sort-Object Name | Select-Object -First 1).Name
}

function Resolve-LogoSourcePath {
    param(
        [string]$LogoPath,
        [string]$RepoRoot
    )

    $explicitPath = Resolve-RepoPath -PathValue $LogoPath -RepoRoot $RepoRoot
    if ($explicitPath -and (Test-Path $explicitPath)) {
        return $explicitPath
    }

    $workspaceRoot = Split-Path -Parent $RepoRoot
    $candidatePaths = @(
        (Join-Path $workspaceRoot "Dupahar_1080p_logo.png"),
        (Join-Path $workspaceRoot "Dupahar_LOGO.png"),
        (Join-Path $workspaceRoot "Dupahar_LOGO(1)(1).png"),
        (Join-Path $RepoRoot "src\main\resources\images\dupahar_logo.png")
    )

    foreach ($candidatePath in $candidatePaths) {
        if (Test-Path $candidatePath) {
            return $candidatePath
        }
    }

    throw "Required logo source not found. Checked: $($candidatePaths -join '; ')"
}

function Test-IsValidPackageVersion {
    param(
        [string]$VersionValue
    )

    if ([string]::IsNullOrWhiteSpace($VersionValue)) {
        return $false
    }

    if ($VersionValue -notmatch '^\d+\.\d+\.\d+\.\d+$') {
        return $false
    }

    $parts = $VersionValue.Split('.')
    if ($parts.Length -ne 4) {
        return $false
    }

    for ($i = 0; $i -lt $parts.Length; $i++) {
        [int]$segmentValue = 0
        if (-not [int]::TryParse($parts[$i], [ref]$segmentValue)) {
            return $false
        }

        if ($segmentValue -lt 0 -or $segmentValue -gt 65535) {
            return $false
        }
    }

    if ([int]$parts[0] -le 0) {
        return $false
    }

    return $true
}

function Assert-StoreVersionRules {
    param(
        [string]$VersionValue
    )

    if (-not (Test-IsValidPackageVersion -VersionValue $VersionValue)) {
        throw "Package version must be in four-part numeric format Major.Minor.Build.Revision with each segment in [0, 65535] and major > 0."
    }

    $parts = $VersionValue.Split('.')
    if ([int]$parts[3] -ne 0) {
        throw "Package version revision segment must be 0 for Store submissions."
    }
}

function Get-MsixIdentityVersion {
    param(
        [string]$MsixPath
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($MsixPath)
    try {
        $manifestEntry = $archive.Entries | Where-Object { $_.FullName -ieq "AppxManifest.xml" } | Select-Object -First 1
        if (-not $manifestEntry) {
            return $null
        }

        $manifestReader = New-Object System.IO.StreamReader($manifestEntry.Open())
        try {
            [xml]$manifestXml = $manifestReader.ReadToEnd()
            return $manifestXml.Package.Identity.Version
        } finally {
            $manifestReader.Dispose()
        }
    } catch {
        return $null
    } finally {
        $archive.Dispose()
    }
}

function Resolve-PackageVersion {
    param(
        [string]$ExplicitVersion,
        [string]$RepoRoot
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitVersion)) {
        Assert-StoreVersionRules -VersionValue $ExplicitVersion
        return $ExplicitVersion
    }

    $artifactsRoot = Join-Path $RepoRoot "artifacts\private-beta"
    if (-not (Test-Path $artifactsRoot)) {
        return "1.0.1.0"
    }

    $existingVersions = @()
    $msixFiles = Get-ChildItem -Path $artifactsRoot -Filter "TerraGIS.Beta.msix" -Recurse -File -ErrorAction SilentlyContinue
    foreach ($msixFile in $msixFiles) {
        $existingVersion = Get-MsixIdentityVersion -MsixPath $msixFile.FullName
        if (Test-IsValidPackageVersion -VersionValue $existingVersion) {
            $existingVersions += [System.Version]$existingVersion
        }
    }

    if ($existingVersions.Count -eq 0) {
        return "1.0.1.0"
    }

    $latestVersion = $existingVersions | Sort-Object -Descending | Select-Object -First 1
    $nextBuild = $latestVersion.Build + 1
    if ($nextBuild -gt 65535) {
        throw "Cannot auto-increment package version because the build segment reached 65535. Pass -PackageVersion explicitly."
    }

    return "{0}.{1}.{2}.0" -f $latestVersion.Major, $latestVersion.Minor, $nextBuild
}

function New-RandomSecureString {
    param(
        [int]$Length = 32
    )

    $chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+[]{}"
    $secure = New-Object System.Security.SecureString
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        for ($i = 0; $i -lt $Length; $i++) {
            $buffer = New-Object byte[] 4
            $rng.GetBytes($buffer)
            $index = [BitConverter]::ToUInt32($buffer, 0) % $chars.Length
            $secure.AppendChar($chars[[int]$index])
        }
    } finally {
        $rng.Dispose()
    }
    $secure.MakeReadOnly()
    return $secure
}

function Test-MsixHasSignature {
    param(
        [string]$MsixPath
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($MsixPath)
    try {
        return [bool]($archive.Entries | Where-Object { $_.FullName -ieq "AppxSignature.p7x" } | Select-Object -First 1)
    } finally {
        $archive.Dispose()
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$PackageVersion = Resolve-PackageVersion -ExplicitVersion $PackageVersion -RepoRoot $repoRoot
Assert-StoreVersionRules -VersionValue $PackageVersion

$resolvedAppImagePath = Resolve-RepoPath -PathValue $AppImagePath -RepoRoot $repoRoot
if (-not $resolvedAppImagePath) {
    $resolvedAppImagePath = Find-LatestAppImagePath -RepoRoot $repoRoot
}

if (-not $resolvedAppImagePath) {
    throw "Could not resolve app-image path. Pass -AppImagePath or run scripts/prepare-private-beta.ps1 first."
}

if (-not (Test-Path $resolvedAppImagePath)) {
    throw "App-image path does not exist: $resolvedAppImagePath"
}

$AppImagePath = $resolvedAppImagePath

$resolvedOutputDir = Resolve-RepoPath -PathValue $OutputDir -RepoRoot $repoRoot
if (-not $resolvedOutputDir) {
    $runDir = Split-Path -Parent (Split-Path -Parent $AppImagePath)
    $resolvedOutputDir = Join-Path $runDir "msix-output"
}
$OutputDir = $resolvedOutputDir

$appExecutableName = Resolve-AppExecutableName -ResolvedAppImagePath $AppImagePath
$logoSourcePath = Resolve-LogoSourcePath -LogoPath $LogoSourcePath -RepoRoot $repoRoot

# Ensure tools are on PATH
$appKitPath = "C:\Program Files (x86)\Windows Kits\10\App Certification Kit"
$winKitX64Path = "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64"
$env:Path = "$appKitPath;$winKitX64Path;$env:Path"

# Verify tools availability
$makeAppx = Get-Command makeappx.exe -ErrorAction SilentlyContinue
$signTool = Get-Command signtool.exe -ErrorAction SilentlyContinue

if (-not $makeAppx) { throw "makeappx.exe not found on PATH" }
if (-not $signTool) { throw "signtool.exe not found on PATH" }

Write-Host "==> MSIX Creation and Signing Helper for TerraGIS"
Write-Host "App-image source: $AppImagePath"
Write-Host "Output directory: $OutputDir"
Write-Host "Resolved executable: $appExecutableName"
Write-Host "Logo source: $logoSourcePath"
Write-Host "Package version: $PackageVersion"
Write-Host "Processor architecture: $ProcessorArchitecture"
Write-Host ""

# Create output directory
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

# Step 1: Create or refresh AppxManifest.xml
$manifestPath = Join-Path $AppImagePath "AppxManifest.xml"
Write-Host "==> Step 1: Writing AppxManifest.xml"

$manifest = @"
<?xml version="1.0" encoding="utf-8"?>
<Package xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities" xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10" xmlns:mp="http://schemas.microsoft.com/appx/2014/mp" xmlns:desktop="http://schemas.microsoft.com/appx/manifest/desktop/windows10" xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10">
        <Identity Name="$IdentityName" Publisher="$IdentityPublisher" Version="$PackageVersion" ProcessorArchitecture="$ProcessorArchitecture" />
    <Properties>
                <DisplayName>$PackageDisplayName</DisplayName>
        <PublisherDisplayName>$PublisherDisplayName</PublisherDisplayName>
        <Logo>Assets\StoreLogo.png</Logo>
    </Properties>
    <Dependencies>
        <TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.17763.0" MaxVersionTested="10.0.26100.0" />
    </Dependencies>
    <Resources>
        <Resource Language="en-us" />
    </Resources>
    <Applications>
        <Application Id="TerraGISBeta" Executable="$appExecutableName" EntryPoint="Windows.FullTrustApplication">
                        <uap:VisualElements DisplayName="$PackageDisplayName" Square150x150Logo="Assets\Square150x150Logo.png" Square44x44Logo="Assets\Square44x44Logo.png" Description="GIS Analysis and Mapping Tool" BackgroundColor="#0D2439">
                            <uap:DefaultTile Wide310x150Logo="Assets\Wide310x150Logo.png" Square310x310Logo="Assets\Square310x310Logo.png" />
                        </uap:VisualElements>
        </Application>
    </Applications>
    <Capabilities>
        <rescap:Capability Name="runFullTrust" />
    </Capabilities>
</Package>
"@

[System.IO.File]::WriteAllText($manifestPath, $manifest, [System.Text.Encoding]::UTF8)
Write-Host "Wrote AppxManifest.xml at $manifestPath"

# Step 2: Ensure Assets directory and required logo PNGs exist
$assetsDir = Join-Path $AppImagePath "Assets"
Write-Host "==> Step 2: Ensuring Assets directory and placeholder images"
New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null

if (-not (Test-Path $assetsDir)) {
    throw "Failed to create Assets directory at $assetsDir"
}

# Generate high-quality required assets from the project logo to satisfy Store tile checks
Add-Type -AssemblyName System.Drawing

function Get-NonTransparentBounds {
    param(
        [System.Drawing.Bitmap]$Bitmap
    )

    $fullRect = New-Object System.Drawing.Rectangle 0, 0, $Bitmap.Width, $Bitmap.Height
    $data = $Bitmap.LockBits($fullRect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $stride = [Math]::Abs($data.Stride)
        $buffer = New-Object byte[] ($stride * $Bitmap.Height)
        [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $buffer, 0, $buffer.Length)

        $left = $Bitmap.Width
        $top = $Bitmap.Height
        $right = -1
        $bottom = -1

        for ($y = 0; $y -lt $Bitmap.Height; $y++) {
            $rowOffset = $y * $stride
            for ($x = 0; $x -lt $Bitmap.Width; $x++) {
                $alpha = $buffer[$rowOffset + ($x * 4) + 3]
                if ($alpha -gt 0) {
                    if ($x -lt $left) { $left = $x }
                    if ($y -lt $top) { $top = $y }
                    if ($x -gt $right) { $right = $x }
                    if ($y -gt $bottom) { $bottom = $y }
                }
            }
        }

        if ($right -lt 0) {
            return $fullRect
        }

        return [System.Drawing.Rectangle]::FromLTRB($left, $top, $right + 1, $bottom + 1)
    } finally {
        $Bitmap.UnlockBits($data)
    }
}

function New-TrimmedBitmap {
    param(
        [string]$SourcePath
    )

    $source = [System.Drawing.Bitmap]::FromFile($SourcePath)
    try {
        $working = New-Object System.Drawing.Bitmap($source.Width, $source.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($working)
            try {
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.DrawImage($source, 0, 0, $source.Width, $source.Height)
            } finally {
                $graphics.Dispose()
            }

            $bounds = Get-NonTransparentBounds -Bitmap $working
            if ($bounds.Width -le 0 -or $bounds.Height -le 0) {
                return $working.Clone((New-Object System.Drawing.Rectangle 0, 0, $working.Width, $working.Height), [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            }

            $paddingX = [Math]::Max(1, [int][Math]::Round($bounds.Width * 0.03))
            $paddingY = [Math]::Max(1, [int][Math]::Round($bounds.Height * 0.03))
            $left = [Math]::Max(0, $bounds.X - $paddingX)
            $top = [Math]::Max(0, $bounds.Y - $paddingY)
            $right = [Math]::Min($working.Width, $bounds.Right + $paddingX)
            $bottom = [Math]::Min($working.Height, $bounds.Bottom + $paddingY)
            $expanded = [System.Drawing.Rectangle]::FromLTRB($left, $top, $right, $bottom)

            return $working.Clone($expanded, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        } finally {
            $working.Dispose()
        }
    } finally {
        $source.Dispose()
    }
}

function New-SquareEmblemBitmap {
    param(
        [System.Drawing.Bitmap]$Bitmap
    )

    $size = [Math]::Max($Bitmap.Width, $Bitmap.Height)
    if ($size -le 0) {
        return $Bitmap.Clone((New-Object System.Drawing.Rectangle 0, 0, $Bitmap.Width, $Bitmap.Height), [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    }

    $square = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($square)
        try {
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $graphics.Clear([System.Drawing.Color]::Transparent)

            # Keep the full logo visible by centering it on a square canvas instead of cropping.
            $offsetX = [int][Math]::Floor(($size - $Bitmap.Width) / 2)
            $offsetY = [int][Math]::Floor(($size - $Bitmap.Height) / 2)
            $graphics.DrawImage($Bitmap, $offsetX, $offsetY, $Bitmap.Width, $Bitmap.Height)
        } finally {
            $graphics.Dispose()
        }

        return $square.Clone((New-Object System.Drawing.Rectangle 0, 0, $size, $size), [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    } finally {
        $square.Dispose()
    }
}

function New-ResizedPng {
    param(
        [string]$SourcePath,
        [string]$DestinationPath,
        [int]$Width,
        [int]$Height,
        [switch]$UseSquareEmblem
    )

    $trimmed = New-TrimmedBitmap -SourcePath $SourcePath
    try {
        if ($UseSquareEmblem) {
            $source = New-SquareEmblemBitmap -Bitmap $trimmed
            $trimmed.Dispose()
        } else {
            $source = $trimmed
        }

        $bitmap = New-Object System.Drawing.Bitmap($Width, $Height)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.Clear([System.Drawing.Color]::Transparent)

                # Keep original aspect ratio and center on the destination canvas.
                $scaleX = [double]$Width / [double]$source.Width
                $scaleY = [double]$Height / [double]$source.Height
                $scale = [Math]::Min($scaleX, $scaleY)

                $drawWidth = [int][Math]::Round([double]$source.Width * $scale)
                $drawHeight = [int][Math]::Round([double]$source.Height * $scale)
                $offsetX = [int][Math]::Floor(($Width - $drawWidth) / 2)
                $offsetY = [int][Math]::Floor(($Height - $drawHeight) / 2)

                $graphics.DrawImage($source, $offsetX, $offsetY, $drawWidth, $drawHeight)
            } finally {
                $graphics.Dispose()
            }
            $bitmap.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $bitmap.Dispose()
        }
    } finally {
        $source.Dispose()
    }
}

$requiredAssets = @(
    @{ Name = "Square44x44Logo.png"; Width = 44; Height = 44; UseSquareEmblem = $true },
    @{ Name = "Square44x44Logo.scale-100.png"; Width = 44; Height = 44; UseSquareEmblem = $true },
    @{ Name = "Square44x44Logo.scale-125.png"; Width = 55; Height = 55; UseSquareEmblem = $true },
    @{ Name = "Square44x44Logo.scale-150.png"; Width = 66; Height = 66; UseSquareEmblem = $true },
    @{ Name = "Square44x44Logo.scale-200.png"; Width = 88; Height = 88; UseSquareEmblem = $true },
    @{ Name = "Square44x44Logo.scale-400.png"; Width = 176; Height = 176; UseSquareEmblem = $true },
    @{ Name = "Square150x150Logo.png"; Width = 150; Height = 150; UseSquareEmblem = $true },
    @{ Name = "Square150x150Logo.scale-100.png"; Width = 150; Height = 150; UseSquareEmblem = $true },
    @{ Name = "Square150x150Logo.scale-125.png"; Width = 188; Height = 188; UseSquareEmblem = $true },
    @{ Name = "Square150x150Logo.scale-150.png"; Width = 225; Height = 225; UseSquareEmblem = $true },
    @{ Name = "Square150x150Logo.scale-200.png"; Width = 300; Height = 300; UseSquareEmblem = $true },
    @{ Name = "Square150x150Logo.scale-400.png"; Width = 600; Height = 600; UseSquareEmblem = $true },
    @{ Name = "Wide310x150Logo.png"; Width = 310; Height = 150; UseSquareEmblem = $false },
    @{ Name = "Wide310x150Logo.scale-100.png"; Width = 310; Height = 150; UseSquareEmblem = $false },
    @{ Name = "Wide310x150Logo.scale-125.png"; Width = 388; Height = 188; UseSquareEmblem = $false },
    @{ Name = "Wide310x150Logo.scale-150.png"; Width = 465; Height = 225; UseSquareEmblem = $false },
    @{ Name = "Wide310x150Logo.scale-200.png"; Width = 620; Height = 300; UseSquareEmblem = $false },
    @{ Name = "Wide310x150Logo.scale-400.png"; Width = 1240; Height = 600; UseSquareEmblem = $false },
    @{ Name = "Square310x310Logo.png"; Width = 310; Height = 310; UseSquareEmblem = $true },
    @{ Name = "Square310x310Logo.scale-100.png"; Width = 310; Height = 310; UseSquareEmblem = $true },
    @{ Name = "Square310x310Logo.scale-125.png"; Width = 388; Height = 388; UseSquareEmblem = $true },
    @{ Name = "Square310x310Logo.scale-150.png"; Width = 465; Height = 465; UseSquareEmblem = $true },
    @{ Name = "Square310x310Logo.scale-200.png"; Width = 620; Height = 620; UseSquareEmblem = $true },
    @{ Name = "Square310x310Logo.scale-400.png"; Width = 1240; Height = 1240; UseSquareEmblem = $true },
    @{ Name = "StoreLogo.png"; Width = 50; Height = 50; UseSquareEmblem = $true }
)

foreach ($asset in $requiredAssets) {
    $assetPath = Join-Path $assetsDir $asset.Name
    New-ResizedPng -SourcePath $logoSourcePath -DestinationPath $assetPath -Width $asset.Width -Height $asset.Height -UseSquareEmblem:$asset.UseSquareEmblem
    if (-not (Test-Path $assetPath)) {
        throw "Failed to create required asset: $assetPath"
    }
}

Write-Host "Created/updated high-quality PNG assets"

# Step 3: Create MSIX package
Write-Host ""
Write-Host "==> Step 3: Creating MSIX package"
$msixPath = Join-Path $OutputDir "TerraGIS.Beta.msix"

# Create mapping file pointing to app-image contents
$appImageAbsPath = (Resolve-Path $AppImagePath).Path

$makeAppxCmd = @(
    "makeappx.exe",
    "pack",
    "/d", ('"' + $appImageAbsPath + '"'),
    "/p", ('"' + $msixPath + '"'),
    "/overwrite"
) -join " "

Write-Host "Command: $makeAppxCmd"
& cmd /c $makeAppxCmd
if ($LASTEXITCODE -ne 0) {
    throw "makeappx pack failed with exit code $LASTEXITCODE"
}

Write-Host "MSIX package created: $msixPath"

# Step 4: Sign MSIX (if certificate provided or test sign requested)
Write-Host ""
if ($SkipSign) {
    Write-Host "==> Step 4: Signing skipped (--SkipSign flag set)"
} elseif ($TestSign) {
    Write-Host "==> Step 4: Creating self-signed certificate for testing"
    
    $certName = $IdentityPublisher
    $certFile = Join-Path $OutputDir "TerraGIS.Beta.Test.pfx"
    
    # Create self-signed cert (requires admin or specific user cert store access)
    $certParams = @{
        FriendlyName = "TerraGIS Beta Test Certificate"
        CertStoreLocation = "Cert:\CurrentUser\My"
        KeyUsage = @("DigitalSignature")
        Type = "CodeSigningCert"
        Subject = $certName
        NotAfter = (Get-Date).AddMonths(12)
    }
    
    try {
        $cert = New-SelfSignedCertificate @certParams -ErrorAction Stop
        Write-Host "Created self-signed certificate with thumbprint: $($cert.Thumbprint)"
        
        # Export to PFX
        $pfxParams = @{
            Cert = "Cert:\CurrentUser\My\$($cert.Thumbprint)"
            FilePath = $certFile
            Password = New-RandomSecureString
        }
        Export-PfxCertificate @pfxParams | Out-Null
        Write-Host "Exported certificate to: $certFile"
        
        # Use it for signing
        $CertificatePath = $certFile
        $CertificatePassword = $pfxParams.Password
    } catch {
        Write-Host "WARNING: Could not create self-signed certificate: $_"
        Write-Host "Proceeding without signature. MSIX will need signing before Store submission."
        $SkipSign = $true
    }
}

if (-not $SkipSign -and $CertificatePath) {
    Write-Host "==> Step 4: Signing MSIX with certificate"
    
    if (-not (Test-Path $CertificatePath)) {
        throw "Certificate not found: $CertificatePath"
    }
    
    $certPasswordBstr = [IntPtr]::Zero
    $certPasswordPlain = ""
    try {
        if ($CertificatePassword) {
            $certPasswordBstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($CertificatePassword)
            $certPasswordPlain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($certPasswordBstr)
        }

        $signCmdBase = @(
            "signtool.exe",
            "sign",
            "/f", ('"' + $CertificatePath + '"'),
            "/fd", "SHA256"
        )
        if (-not [string]::IsNullOrWhiteSpace($certPasswordPlain)) {
            $signCmdBase += @("/p", ('"' + $certPasswordPlain + '"'))
        }

        $signCmd = $signCmdBase + @(('"' + $msixPath + '"'))
        $timestampingSignCmd = $signCmdBase + @("/tr", "http://timestamp.comodoca.com/rfc3161", "/td", "SHA256", ('"' + $msixPath + '"'))
        $signCmd = $signCmd -join " "
        $timestampingSignCmd = $timestampingSignCmd -join " "
    } finally {
        if ($certPasswordBstr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($certPasswordBstr)
        }
    }
    
    Write-Host "Signing package..."
    & cmd /c $timestampingSignCmd
    if ($LASTEXITCODE -ne 0) {
        Write-Host "WARNING: Timestamped signing failed with exit code $LASTEXITCODE. Retrying without timestamp."
        & cmd /c $signCmd
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Host "WARNING: signtool sign returned exit code $LASTEXITCODE"
        Write-Host "MSIX may still be usable for testing but will fail Store submission without valid signature"
    } else {
        Write-Host "MSIX successfully signed"

        Write-Host "Verifying signature..."
        $verifyCmd = "signtool.exe verify /pa /all " + ('"' + $msixPath + '"')
        $oldErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $verifyOutput = & cmd /c $verifyCmd 2>&1
        $ErrorActionPreference = $oldErrorActionPreference
        $verifyOutput | Where-Object { $_ }

        if ($LASTEXITCODE -ne 0) {
            Write-Host "WARNING: Signature verification returned exit code $LASTEXITCODE"
            Write-Host "Self-signed test certificates commonly fail chain trust verification when the root is not trusted."
        }
    }
} elseif (-not $SkipSign) {
    Write-Host "==> Step 4: No certificate provided, MSIX will not be signed"
    Write-Host "To sign, provide: -CertificatePath <pfx_file> -CertificatePassword (Read-Host -AsSecureString)"
    Write-Host "Or use -TestSign to create a self-signed test certificate"
}

if ($RequireSignature) {
    if (-not (Test-MsixHasSignature -MsixPath $msixPath)) {
        throw "Package signature was required but AppxSignature.p7x was not found in the package."
    }
    Write-Host "Signature presence check passed (AppxSignature.p7x found)"
}

# Step 5: Summary
Write-Host ""
Write-Host "==> Step 5: Summary"
Write-Host "MSIX package: $msixPath"
Write-Host "Identity version: $PackageVersion"
if (Test-Path $msixPath) {
    $size = (Get-Item $msixPath).Length / 1MB
    Write-Host "Size: $([math]::Round($size, 2)) MB"
}

Write-Host ""
Write-Host "==> Next Steps:"
Write-Host "1. For Store submission: Apply production certificate signing"
Write-Host "2. Create .msixupload bundle if required by Partner Center"
Write-Host "3. Upload to Microsoft Store via Partner Center"
Write-Host "4. Select 'Private Audience' and invite testers"
Write-Host ""
Write-Host "MSIX creation and signing completed."

