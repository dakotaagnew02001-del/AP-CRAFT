# v4.8 Banking integration

Implemented a persistent Village Banker NPC and a fictional share market in `VillagerBank.java`.

- Right-click the banker to open clickable chat actions (no typed commands required).
- Deposit and withdraw $100 increments from the shared wallet economy.
- Buy and sell Village Aviation Co. shares at the current game-day price.
- Savings and share balances persist in `apcraft-bank.json` in the world save.
- Price is deterministic and cyclical; not tied to real securities.
- Internal `/apbank` command handlers exist only to support clickable chat actions.

## Known limitations

- This is a source-only release, not a verified playable JAR.
- The banker is temporarily spawned near the player rather than placed in a dedicated bank building.
- Savings do not accrue interest; stock prices follow a fixed repeating 20-day cycle.
- Existing modules still require compilation and gameplay integration testing.
- Cooking and expanded shopping remain future work.
