# A&P Craft v5.9 — Build validation hardening

- Added checks that all five portable storage items have crafting recipes and item models, and that recipe results match their registered IDs.
- Added checks against accidentally bundling compiled `.class` files or old `.jar` artifacts inside the source package.
- Added GitHub Actions upload of Gradle build diagnostics when the build fails.
- Preserved existing Java sources and gameplay features unchanged.

## Verification
`python3 scripts/preflight.py` passed (38 Java files inspected; zero reported failures). This is **not** Java compilation or an in-game test. The environment does not provide Gradle or cached Minecraft/Fabric dependencies. Use GitHub Actions to attempt a full build and review compiler output.
