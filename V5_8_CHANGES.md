# v5.8 — Build reliability pass

- Removed a mistakenly nested older copy of the project (`apcraft_v54/`) from the distributed source. This avoids editing or building the wrong project.
- Added a GitHub Actions build workflow using Java 17 and Gradle 8.7. It will attempt a real `gradle clean build` when the project is pushed to GitHub and report compilation failures.
- Expanded the dependency-free preflight checker with packaging, metadata and recipe checks.
- Updated project version to 5.8.0.

**Verification:** Static preflight and archive checks only in this environment. Gradle is not installed here; no compiled JAR or Minecraft gameplay test is claimed.
