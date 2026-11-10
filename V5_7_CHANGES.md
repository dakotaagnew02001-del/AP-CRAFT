# v5.7 — Field equipment and bag data integrity

- Added a craftable 9-slot Mechanic Tool Roll and 27-slot Field Service Case.
- Corrected a data-loss risk: restoring a bag inventory previously called markDirty and overwrote the NBT list during loading. Inventory restoration now suspends NBT writes until complete.
- Existing bag types and anti-nesting restrictions retained.
- Item textures use temporary vanilla item sprites.
- Source has not been compiled or tested in Minecraft. Wearable bags, placed rolling toolboxes and vehicle cargo integration remain future work.
