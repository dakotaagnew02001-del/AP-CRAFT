# v5.3 — cargo and distance tracking

- Cargo-capable ground vehicles now spawn as chest boats with persistent 27-slot inventory: pickup/truck classes, commercial vehicles, off-road vehicles, sedans, and wagons. Motorcycles and sports cars remain regular boats without cargo inventory.
- Existing pickup shulker-box loading now applies to all chest-boat ground vehicles and checks main hand to avoid double interactions.
- Very short travel segments are no longer discarded from fuel/wear tracking. Fuel and condition are clamped to zero.
- Existing vehicles do not automatically transform; newly purchased eligible vehicles have cargo inventory.
- All visuals are still vanilla boat placeholders; this is not a 3D vehicle entity implementation.
- No Gradle compilation or in-game test performed.
