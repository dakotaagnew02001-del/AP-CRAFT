# A&P Craft — Product vision and release gates

## Vision
An aviation-maintenance RPG: learn FAA-grounded General/Airframe/Powerplant concepts, practice simulated procedures, earn fictional wages, purchase tools and aircraft, own shops, hire NPC employees, and expand an aviation business. The Apache is an optional arcade flying/combat vehicle, not a teaching gate.

## Player loop
1. Start as Shop Helper with starter funds and an accessible shop.
2. Learn from source-indexed FAA lessons; review source before attempting a practical.
3. Complete work orders and physical practical stations to gain experience and wages.
4. Upgrade tools, accept more contracts, purchase aircraft, and open businesses.
5. Grow a team and expand into a multi-location aviation company.

## Quality gates (must pass before claiming a playable release)
- Resolve Java compile errors under Fabric 1.20.1 and Java 17.
- Test world generation, new-world startup, save/reload, and multiplayer access.
- Test economy consistency: spending, debt, earnings, staff, ownership, persistence.
- Audit FAA lessons and practicals against exact FAA publication sections and remove unsupported statements.
- Replace generic text choices with real instrument readings, interactive parts, and validated practical grading.
- Confirm all aircraft flight/vehicle systems function; test Apache weapons and passenger handling.
- Package a tested installable mod JAR with documented dependencies.

## Source-integrity policy
FAA publications and the ACS determine the educational content. The game's fictional career titles, pay, XP, and business simulation are **not FAA guidance**. Aircraft-specific values must come from applicable maintenance data. Never claim an FAA endorsement or certification from in-game completion.

## Current milestone — v4.0
Adds persistent RPG career ranks and a journal (`/career`, `/career journal`, `/career ranks`). Paid training work recorded through the central economy reward path increases career progress. Career rank is separate from real-world certification and currently does not gate aircraft maintenance or business ownership. This is a foundation for later in-world NPC conversations and mission boards.
