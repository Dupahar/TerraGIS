# TerraGIS Known Limitations

Version: 1.0.0-beta.6
Date: 2026-04-17

## Build and Packaging

1. MSIX packaging and publisher-matching signing are complete for Submission 2.
   - Submission 1 certification findings were remediated (tile quality + privacy URL).
   - Rebuilt package version for resubmission: 1.0.1.0.
2. Local signature verification may show chain trust warning on machines where the signing root is not trusted.

## Validation Scope

1. Clean-machine validation matrix is not yet complete.
2. Local smoke launch is validated, but cross-machine validation is still pending.

## Product Scope

1. Surveyor persona is closest to production hardening; Hydrologist and Carbon Auditor flows remain in progress.
2. Advanced cartographic styling and large-raster performance optimization are still queued roadmap items.

## Submission Note (Private Audience)

Use these notes in Partner Center:
- This build is a private beta candidate for controlled audience validation.
- Functional validation is complete on developer environment and in automated tests.
- Submission 2 package is prepared for certification resubmission; clean-machine evidence capture continues in parallel.
