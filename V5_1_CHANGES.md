# v5.1 — Ground vehicle ownership and garage interactions

- Added `GroundVehicleSystems` for all 26 ground-vehicle catalog entries.
- Sneak-right-click an owned vehicle with an empty hand to inspect fuel and condition.
- Sneak-right-click with coal/charcoal to refuel (+25), or iron ingot to service (+20).
- Fuel consumption and condition wear increase as a ridden vehicle travels; state is stored in entity command tags and survives world saves.
- Low fuel/condition slows the boat placeholder, but this is **not** a real driving physics model.
- This is source-only; no Gradle compile or Minecraft runtime test has been completed.
