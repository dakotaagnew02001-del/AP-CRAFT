# A&P Craft — build and gameplay verification

This is **source code**, not a ready-to-install mod JAR. No successful Gradle compile or Minecraft runtime test has been recorded for this version.

## Prerequisites
- Minecraft Java Edition 1.20.1 (not Bedrock)
- Java 17 JDK
- Gradle 8.x installed locally (this project does not yet include a Gradle wrapper)
- Internet access to download Fabric Loom, Minecraft, mappings and Fabric API dependencies

## Build
1. Extract the source ZIP into a folder.
2. Open a terminal in the folder containing `build.gradle`.
3. Run `python3 scripts/preflight.py` for basic source/resource checks (this does not compile Java).
4. Run `gradle build --stacktrace` to compile the mod.
5. If successful, find the distributable JAR under `build/libs/` (avoid the `-sources.jar` file).
6. Install Fabric Loader for Minecraft 1.20.1 and Fabric API 0.92.2+1.20.1, then put the compiled mod JAR into the game's `mods` folder.

## Minimum in-game smoke tests (after a successful build)
- Launch a fresh test world and check the log for registration errors.
- Verify the mechanic menu opens with `M`.
- Use `/fleet shop`, purchase an Apache through the intended in-game dealer flow, and verify it spawns as `apcraft:apache` and renders correctly.
- Board the Apache and test takeoff, landing, rotor animation, weapon controls and two-seat interaction. Note any behavior that still depends on legacy boat physics.
- Test `/diagnose aircraft` and a full Cessna troubleshooting case, including an incorrect answer and progress persistence across restart.
- Test saving/reloading a world with owned vehicles and player economy data.
- Check dedicated server startup separately; client rendering classes must never be loaded on the server.

## Known limitations
- The Apache flight model is simplified and the aircraft still extends Minecraft's boat entity.
- Civilian maintenance scenarios are educational approximations, not approved maintenance instructions.
- Existing versions have not been verified through a full compilation or game test in this environment.
- If Gradle reports compilation errors, those must be fixed before this is installable.

### v3.7 academy smoke test
1. Run `/faa lessons`; confirm 12 lessons.
2. Run `/faa start 1`, `/faa source`, `/faa answer 1`, `/faa complete`.
3. Start lesson 2, restart the world/server, then `/faa status` to verify persistence.
4. Verify that `/faa complete` cannot bypass the knowledge question.
5. Confirm older training modules remain accessible but are treated as legacy/unverified.

## v3.9 interaction smoke test (not yet executed)
1. Build with Java 17 and Gradle/Fabric Loom, then start a fresh 1.20.1 test world.
2. Run `/practical benches` in open space and right-click each of the four stations.
3. Confirm clickable chat choices, wrong-answer feedback, completion, and `/practical status` after relogging.
4. Restart the server and verify the same benches remain interactive.
5. Replace one station block and verify it no longer starts the training session.
6. Check other blocks retain their vanilla right-click behavior.

## v5.8 CI build
Push the extracted project to a GitHub repository. The workflow in `.github/workflows/build.yml` runs the source preflight, downloads dependencies through Gradle, and attempts a Java 17 build. A failed build will display compiler errors in the Actions log; only successful builds upload JAR artifacts. This workflow has **not** been run as part of this release.
