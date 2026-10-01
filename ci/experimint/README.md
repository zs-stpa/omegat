# experimInt pipeline

Workflow `.github/workflows/experimint.yml`: on every push of the integration
branch run upstream-style quality checks and build `installMacArmDist` on a
macos-14 runner, patch/ad-hoc-sign/zip the app (`package-mac.sh`) and publish
it as a pre-release on this fork (`publish-release.sh`, tag
`experimental-integration/YYYY-MM-DD-sha7`, retention 14). Build jobs run with
a read-only token; only the publish job, which builds nothing, may write.
Read `DISCLAIMER-release.md` before using a published build.
