# zs-stpa/omegat — personal OmegaT fork

Not the OmegaT project. Upstream: https://sourceforge.net/p/omegat/code/

This default branch only holds the release publisher for the unofficial
**Ωt-experimInt** pre-releases (see the releases page and its disclaimer).
OmegaT source: branch `master` (mirror of upstream) and `topic/stpa/*`.

- Build: `.github/workflows/experimint.yml` on `integration/all-open` /
  `experimental-integration`, read-only token, uploads packages and release
  facts (base SHA, ingredient list) as artifacts.
- Publish: `.github/workflows/experimint-publish.yml` here, triggered by a
  fully green build run (`workflow_run`): validates the artifacts
  (`scripts/check-dist.sh`), writes the release text from `text/`
  (`scripts/compose-body.sh`) and creates the pre-release
  (`scripts/publish-release.sh`: draft, upload, publish, retention 14).

Trust boundaries:

- Only the publisher holds a write token and it never runs integration code.
  The integration rebuild refuses any ingredient other than the pipeline
  branch that touches `.github/`, because a workflow file there could request
  write access itself. Rulesets protect this branch and `master` against
  updates by GitHub Actions.
- The packages themselves are built from every integrated branch; only their
  names and sizes are checked here, not their content.
- A newer build run replaces a publish run still waiting in the queue.
- Works with "immutable releases" (recommended): assets are attached to a
  draft before publishing, a rerun of a published tag does nothing, and
  retention deletes the release before its tag. A deleted release's tag name
  can't be reused, so republishing the same commit after a manual delete
  fails.
