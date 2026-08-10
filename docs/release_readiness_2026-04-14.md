# TerraGIS Release Readiness (Private Beta)

Date: 2026-04-17
Candidate: 1.0.0-beta.6
Artifact: artifacts/private-beta/1.0.0-beta.6-20260414-012059

## 1. Gate Status Snapshot

| Gate | Status | Evidence |
|---|---|---|
| Java 25 toolchain available | Pass | java 25.0.2 in release evidence |
| App-image packaging | Pass | beta.6 app-image created (20260414-012059) |
| Local smoke launch | Pass | process started and stayed alive >20s |
| Maven tests | Pass | 62 tests, 0 failures/errors |
| Maven verify | Pass | JaCoCo line coverage gate met (>= 0.15) |
| makeappx.exe availability | Pass | Found at C:\\Program Files (x86)\\Windows Kits\\10\\App Certification Kit |
| signtool.exe availability | Pass | Found at C:\\Program Files (x86)\\Windows Kits\\10\\App Certification Kit |
| MSIX packaging | Pass | `scripts/create-and-sign-msix.ps1` creates `TerraGIS.Beta.msix` successfully |
| MSIX signing (publisher-matching cert) | Pass | Signed with `CN=E8B9DFF8-00AF-4BB0-A12B-23EBCE61FEE8` certificate on 2026-04-17 |
| MSIX verify trust chain | Warning | Local root trust warning may appear on build machine; package validated in Partner Center |
| Partner Center package validation | Pass | Rebuilt package prepared for Submission 2 with corrected assets/privacy/version |
| Partner Center submission status | Published | Submission 2 certified and published to Microsoft Store |
| Clean-machine validation matrix (3 machines) | Pending | not yet completed |

## 2. Current Release Blockers

None for Store publication. The packaging/signing path is complete and the published build is live.

1. Clean-machine validation matrix has not been completed (Win10/Win11 x3).
2. Submit Submission 2 and await Microsoft certification result.

## 3. Non-Blocking Warnings

- OpenJFX effective model warnings during dependency collection.
- Deprecated timezone and Unsafe warnings during test runtime.

## 4. Exit Criteria Before Private Audience Invite

- [x] `mvnw clean verify` passes without `-SkipVerify` packaging workaround.
- [x] `makeappx.exe` and `signtool.exe` available and verified.
- [x] Signed MSIX/MSIXUPLOAD created with matching production publisher identity.
- [x] Package version moved to Store-compliant unique value `1.0.1.0` (revision segment `0`).
- [x] Privacy policy URL prepared for Partner Center Properties (`https://dupahar.github.io/TerraGIS/`).
- [ ] Three clean-machine validation runs completed and documented.
- [ ] Known limitations included in Partner Center submission notes.

## 5. Recommended Next Commands

From TerraGIS root:

```powershell
# Ensure JDK 25 is active
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Re-run full quality gate
.\mvnw.cmd clean verify

# Tool availability checks
Get-Command makeappx.exe -ErrorAction SilentlyContinue
Get-Command signtool.exe -ErrorAction SilentlyContinue
```

## 6. Immediate Remediation Plan

1. Replace package in Partner Center Submission 2 using the rebuilt signed MSIX (version `1.0.1.0`).
2. Execute and record clean-machine validation matrix.
3. Capture post-certification publish evidence and tester onboarding evidence.
4. Validate private audience testers can download and launch.
