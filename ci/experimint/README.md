# experimInt pipeline

Workflow `.github/workflows/experimint.yml`: on every push of the integration
branch run upstream-style quality checks, build `installMacArmDist` on a
macOS runner and patch/ad-hoc-sign/zip the app (`package-mac.sh`), build
`installLinux64Dist` and `winJRE64Prep` on Linux and pack them as portable
archives without installers (`package-portable.sh`; the upstream installers
would replace an official installation), and upload all packages as
artifacts. This workflow only has a read-only token: it runs code from merged
third-party PRs. Publishing lives on the fork's default branch
`experimint/publisher` (`experimint-publish.yml`, `workflow_run`): after a
fully green run it validates the artifacts and creates the pre-release (tag
`experimental-integration/YYYY-MM-DD-sha7`, retention 14).
Release text and disclaimer live on `experimint/publisher` (`text/`);
`release-body.sh` only hands over base SHA and ingredient list.
