# TerraGIS Private Audience Packaging Checklist

Date: 2026-04-17

## 0. Current Status Snapshot

- App-image packaging: Completed
- MSIX packaging: Completed
- Test-signing (self-signed cert): Completed
- Production cert signing: Completed
- Partner Center package validation: Completed
- Store submission status: In certification (Submission 1)
- Clean-machine matrix (3 machines): Pending

## 1. Preflight

- Ensure Java 25 is installed at `C:/Program Files/Java/jdk-25.0.2`.
- Confirm branch is `beta/store-private-audience`.
- Confirm local workspace builds with no blocking errors.

## 2. Run Packaging Prep Script

From TerraGIS root:

```powershell
.\scripts\prepare-private-beta.ps1 -VersionTag 1.0.0-beta.1
```

MSIX packaging/signing helpers:

```powershell
# Package only
.\scripts\create-and-sign-msix.ps1 -SkipSign

# Package + test sign
.\scripts\create-and-sign-msix.ps1 -TestSign

# Sign existing package with explicit password prompt
$certPassword = Read-Host -AsSecureString
.\scripts\create-and-sign-msix.ps1 -CertificatePath "C:\path\to\prod-cert.pfx" -CertificatePassword $certPassword
```

Script outputs:
- app bundle under `artifacts/private-beta/<version>-<timestamp>/`
- evidence markdown under `docs/release_evidence/`

## 3. Evidence Required

- `mvnw clean verify` success (captured in evidence file).
- Jar checksum SHA256 recorded.
- App-image generation status recorded.
- Toolchain versions recorded.
- Store prerequisite tools presence (`makeappx`, `signtool`) recorded.

## 4. Manual Validation (Clean Machines)

Run on each machine:
- Copy the candidate app-image into a fresh, user-owned folder.
- Launch the app from the app-image output, not from the dev workspace.
- Confirm first window appears in <= 20 seconds.
- Verify open vector, open raster, pan/zoom, and export.
- Verify Send Diagnostics creates a bundle and records the output path.
- Verify read-only mode appears when the license is revoked or expired.

Validation matrix:

| Machine | OS | State | Result | Launch time | Notes |
|---|---|---|---|---|---|
| 1 | Windows 10 | Clean |  |  |  |
| 2 | Windows 11 | Clean |  |  |  |
| 3 | Windows 11 | Clean |  |  |  |

Capture per machine:
- Windows version and build number
- App-image path used
- Pass/fail result
- Launch time in seconds
- Diagnostics bundle path, if created
- License state used for the test
- Issue notes or screenshots

Record a failure if any of these occur:
- App does not start.
- First window takes longer than 20 seconds.
- Vector/raster open fails on a valid sample file.
- Export fails.
- Diagnostics bundle is not created.
- Read-only mode does not activate after revocation or expiry.

## 5. Store Submission Notes (Private Audience)

- Audience: private testers only.
- Include known limitations section.
- Include privacy notice URL.
- Include support contact channel for crash bundle sharing.

## 6. Exit Criteria Before Invites

- Three clean-machine validation passes.
- No Sev 1 defects.
- Diagnostics bundle validated.
- License read-only flow validated.
- MSIX signed with production certificate matching Partner Center identity.
- Submission remains in certification or passes certification with no blocking findings.

## 7. Validation Result Template

Use this snippet for the release evidence note after each machine run:

```markdown
- Machine:
- OS:
- App-image path:
- Launch time:
- Vector open:
- Raster open:
- Pan/zoom:
- Export:
- Diagnostics bundle:
- License state:
- Read-only mode:
- Result:
- Notes:
```

## 8. Ready-to-Paste Run Log

Use this block as-is in the release evidence file and fill one machine at a time:

```markdown
## Clean-Machine Validation Run Log

### Machine 1
- OS:
- Build:
- App-image path:
- Launch time:
- Vector open:
- Raster open:
- Pan/zoom:
- Export:
- Send Diagnostics bundle:
- License state:
- Read-only mode:
- Result:
- Notes:

### Machine 2
- OS:
- Build:
- App-image path:
- Launch time:
- Vector open:
- Raster open:
- Pan/zoom:
- Export:
- Send Diagnostics bundle:
- License state:
- Read-only mode:
- Result:
- Notes:

### Machine 3
- OS:
- Build:
- App-image path:
- Launch time:
- Vector open:
- Raster open:
- Pan/zoom:
- Export:
- Send Diagnostics bundle:
- License state:
- Read-only mode:
- Result:
- Notes:
```
