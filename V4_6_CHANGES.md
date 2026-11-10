# A&P Craft 4.6 — Starter airport annex

A new 27×15-block employment and training annex is placed at the starter airport when its chunks are loaded. Existing worlds receive the annex once, tracked by `apcraft-airport-hub-v46.txt` in the save root. This is an in-world hub, not a full procedural airport overhaul.

- Hangar Dispatcher: right-click for paid work orders.
- FAA Training Instructor: right-click for lesson choices.
- Job-board lectern: right-click for work orders.
- Four physical practical benches: right-click to start safety-wire, multimeter, weight-and-balance, and magneto-timing decision exercises.
- Interior partitions, windows, lighting, and a walkway to the apron.

**Limitations:** These are clickable chat-driven decision exercises, not 3D tool manipulation. Existing shop and dealer features are unchanged. Existing onboarding NPCs may also appear near players; a future cleanup should consolidate them. No claims of FAA approval. Build and in-game testing are still required.

**Important:** The original airport is created only when its required chunks are loaded. The annex also waits for its own chunks. It does not force-load chunks or overwrite an existing airport a second time. Because it is built above terrain, the new annex may require terrain-aware foundations in uneven worlds.
