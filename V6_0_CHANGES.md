# v6.0 — Build diagnostics

- GitHub Actions now records full Gradle output to `build/diagnostics/gradle-build.log`.
- `set -o pipefail` ensures compilation failures remain failures when piped through `tee`.
- On failed builds, logs and any test reports are uploaded as downloadable artifacts.
- Source preflight checks Java package paths, public type filenames, project version and workflow logging.
- **No successful compilation or in-game test is claimed.** Local environment has no Gradle and cannot reach the Gradle download host.
