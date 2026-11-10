# A&P Craft v3.5 — Civilian Troubleshooting Expansion

This source update adds a persistent, three-stage civilian aircraft diagnostic training prototype for Cessna 172, Beechcraft King Air 200 and Piper PA-28. Each aircraft has access to three generic training cases (ignition, charging and pitot-static). They are not type-specific approved maintenance data.

Commands: `/diagnose aircraft`, `/diagnose start <aircraft 1-3> <fault 1-3>`, `/diagnose choose <1-3>`, `/diagnose status`, `/diagnose cancel`.

Training sessions persist per player in the world save folder. The Apache remains an arcade vehicle; no additional Apache maintenance training has been added.

**Status:** Source-only prototype. Not compiled, launched or gameplay-tested. Existing modules and aircraft may contain integration defects. Do not use for real maintenance decisions.

---

plugins { id 'fabric-loom' version '1.6-SNAPSHOT' }
version = '2.8.0'
group = 'com.apcraft'
base { archivesName = 'apcraft' }
repositories { mavenCentral() }
dependencies {
 minecraft 'com.mojang:minecraft:1.20.1'
 mappings 'net.fabricmc:yarn:1.20.1+build.10:v2'
 modImplementation 'net.fabricmc:fabric-loader:0.15.11'
 modImplementation 'net.fabricmc.fabric-api:fabric-api:0.92.2+1.20.1'
}
processResources { inputs.property 'version', project.version; filesMatching('fabric.mod.json') { expand version: project.version } }
tasks.withType(JavaCompile).configureEach { it.options.release = 17 }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }


## v2.8 Apache crew training prototype
- Two-seat Apache uses the existing vanilla boat passenger seats.
- Sneak-right-click the Apache to inspect the crew panel (when outside); seated players can use `/apachecrew panel`.
- Sensor modes: DAY, NIGHT and a text-only THERMAL simulation; existing spyglass night vision remains available.
- Hard horizontal impacts record simplified airframe damage, and severe damage slows the vehicle.
- The owner may pay for a training repair when grounded.
- This is **not** a custom Apache model, targeting system, genuine thermal sensor, or realistic damage model.
- Source has not been compiled or gameplay-tested.


## v2.9 — AH-64 component maintenance prototype
- AH-64 boat prototype now has five independently tracked systems: main rotor, tail rotor, transmission, hydraulics, and turbine.
- Component condition persists as tags on the vehicle entity. Collision events degrade rotor and transmission condition.
- Board the Apache and use the clickable **Inspect components** entry in the crew panel (`/apachecrew panel`).
- Select a component's clickable service option and hold its indicated vanilla training tool. Repairs cost in-game money, require ownership, and require a stopped grounded aircraft.
- Severe component damage slows the arcade aircraft prototype.
- **Not yet implemented:** custom 3D helicopter entity, animated rotors, separate cockpit views, true thermal rendering, real aircraft performance models, real maintenance procedures.
- **Uncompiled source prototype**; validate in a Minecraft test environment before use.


## v3.0 — Apache visual rig prototype

A purchased AH-64 (fleet model 8) now receives a temporary, entity-mounted visual rig when players are nearby. Nine invisible armor stands wear block helmets to indicate a fuselage, tail boom, rotating main rotor, and tail rotor. The rig tracks the existing boat entity, including while moving. It is removed when the Apache is outside player tracking range.

**Limitations:** This is NOT a custom helicopter entity, textured 3D mesh, or realistic rotor animation. It retains the boat vehicle, its seats, and arcade physics. Armor-stand head blocks are visual markers, not a complete Apache model. The project has not been compiled or tested.


## v3.1 — Apache custom entity foundation

New Apache purchases now spawn a dedicated `apcraft:apache` entity type (`ApacheEntity`), registered in `ApacheAircraft`. The existing boat controls, two-seat crew, maintenance tags, night-vision and projectile systems continue to operate through the subclass. A client renderer is registered using Minecraft's boat renderer as a **temporary placeholder**; the v3.0 rotating armor-stand rig still provides rotor markers. Existing tagged vanilla-boat Apaches are unchanged.

This is an entity-registration milestone, **not** a finished AH-64 model, realistic helicopter flight model, or proper rotor mesh. It is not compiled or gameplay-tested.


## v3.2 — Custom Apache low-poly model and rotor animation
- Adds a dedicated textured client-side Apache renderer, replacing the boat renderer for **new ApacheEntity instances**.
- Adds low-poly fuselage, canopy, stub wings, landing gear, mast, tail boom, main rotor and tail rotor.
- Rotors animate client-side; main rotor spins faster while crew are aboard and slows if rotor condition is severely degraded.
- The older armor-stand visual rig remains only for legacy tagged boat Apaches.
- This is a stylized prototype, not a detailed AH-64 model. Boat movement/physics and seat layout are unchanged.
- Build and gameplay validation have not been completed.

## v3.3: Apache flight-training controls (prototype)

While seated in a newly purchased AH-64, use the crew panel and select **Flight controls and instruments**. The new clickable panel provides electrical power, two simplified engine switches, collective levels 0–5, left/neutral/right pedal settings, and a simulated instrument readout. Enable existing fleet flight mode to activate the custom movement loop. A single engine produces reduced rotor RPM; insufficient rotor RPM prevents positive lift. Collective changes vertical movement, the pilot's look direction supplies simple forward motion, and pedals change yaw.

**Not a real AH-64 flight model:** there is no true torque/temperature model, independent cyclic axis, turbine start sequence, aerodynamic translational lift, autorotation, or failure-mode certification. Values are fictional training values. Flight state is currently in-memory and resets on restart. The custom movement is only applied to the new Apache entity, not legacy boat-based Apache purchases. This project has not been compiled or tested in Minecraft.


## v3.4 — Apache failure-to-flight integration

The custom Apache flight model now reads persistent component condition tags from the existing Apache maintenance panel. Main rotor, transmission and turbine deterioration reduce achievable rotor RPM and lift; degraded hydraulics reduce cyclic/yaw response; tail rotor deterioration reduces yaw authority; airframe deterioration reduces forward speed. Cockpit panel and instruments report power demand and caution status, with throttled in-flight warnings. Maintenance via the existing component service panel restores system performance.

**Training only:** condition thresholds and power numbers are invented gameplay approximations, not AH-64 engineering limits or authorized maintenance guidance. Source is not compiled or gameplay-tested.

## v3.6 — Build-readiness pass
- Added `scripts/preflight.py` to check essential source, entrypoints, resources and Apache/civilian-training wiring.
- Added `BUILD_AND_TEST.md` with reproducible build instructions, gameplay smoke tests and explicit known limitations.
- This is a QA/documentation milestone, **not** a confirmed compiled release or a new gameplay feature.

## v3.7 — FAA-sourced academy foundation (new)

Adds `/faa` with 12 General/Airframe/Powerplant knowledge lessons and follow-on **simulated practical reviews**, organized by FAA publication and ACS subject. Sessions persist per player. Commands: `/faa lessons`, `/faa start <number>`, `/faa answer <1-3>`, `/faa status`, `/faa source`, `/faa complete`, `/faa cancel`.

The knowledge content is paraphrased from FAA handbook topics and references FAA-S-ACS-1. The practical stages are game-designed prompts and are NOT directly published FAA procedures. No claim of exact FAA question wording, DME endorsement, hands-on assessment, or aircraft-specific maintenance approval is made. See `FAA_SOURCE_POLICY.md` for primary references and limitations.

**Legacy content caveat:** Older `/apcraft quiz`, `/work`, `/diagnose`, and other training activities have NOT yet been individually checked against FAA publications and should not be represented as FAA-verified. This update adds a new traceable academy; it does not yet replace all older material.

**Build status:** Source-only, static preflight checked; not compiled or gameplay-tested.

## v3.8 — FAA practical training stations

Four **interactive command-driven simulations** have been added: safety-wire inspection, multimeter bench, weight-and-balance desk, and magneto timing trainer. Each has three sequential decisions, explanations for wrong choices, save/resume, and a named FAA handbook topic. `/practical list`, `/practical start 1`, `/practical choose 1`, `/practical status`, `/practical source`, `/practical cancel`. `/practical benches` places four optional visual workbench blocks in empty space near the player. The workbench blocks are **not clickable training GUIs**, and these are decision-based simulations, **not** hands-on mouse manipulation of safety wire or test equipment. Aircraft-specific tolerances and timing values are deliberately excluded.

FAA source reference: https://www.faa.gov/regulations_policies/handbooks_manuals/aviation. Exact edition/page-level verification and an audit of older content are still pending. Do not represent these exercises as verbatim FAA material, FAA-endorsed, or FAA practical-exam sign-offs.

## v3.9 — Interactive practical benches
Use `/practical benches` in a clear area. Four vanilla-block training stations are placed without overwriting blocks. Right-click one to open its corresponding exercise, and click an answer in chat. Bench coordinates are saved per world in `apcraft-practical-benches.txt`; active exercises save per player. Breaking or replacing a bench disables its registered interaction. These remain scenario-based training interactions, **not** manipulative 3D FAA practical examinations.

**Source verification status:** FAA publication/topic labels are supplied, but precise page/section checks and a full item-by-item source audit are outstanding. Do not represent any lesson as verbatim FAA material or FAA-approved testing.

## v4.0 — Aviation RPG direction and career journal
- Persistent fictional career progression, seven ranks, and milestones (`/career`, `/career ranks`).
- Completing work through the central `APCraft.rewardTraining` path records a career job; other gameplay reward paths may not yet contribute.
- No certification privileges are implied by rank. FAA-based training and the fictional economy are deliberately separate.
- See `ROADMAP.md` for product vision and release quality gates.
- **Uncompiled source prototype**; not yet verified in Minecraft.

## v4.1 — In-world maintenance job board

Operators can use `/jobboard place` to place a lectern-based maintenance job board two blocks ahead in an empty space. Players right-click the lectern to browse four clickable job offers and accept one, using the existing paid `/work` workflow. `/jobboard open` provides an accessibility fallback. Board positions persist in `apcraft-job-boards.txt` per save and are removed when their lectern is replaced and next interacted with. Existing paid work orders now also advance the RPG Career Journal (previously only some training rewards did). These work orders are simplified fictional exercises; do not use them as aircraft-specific procedures. No FAA authorization is implied by RPG ranks.

**Validation status:** Source package only. Minecraft compile and gameplay validation are still required.

## v4.2 — In-world hangar dispatcher

Authorized operators can use `/jobboard dispatcher` to spawn a persistent named
Hangar Dispatcher villager. Right-clicking this NPC opens the existing paid work
order choices (clickable chat commands). The villager has a dedicated entity tag
so normal villagers retain vanilla interactions. Existing lectern boards remain
supported. This is a first NPC interaction layer, not a complete employment AI
or a custom graphical interface. Existing work orders are simplified training
simulations and should not be treated as FAA-approved procedures.

**Build status:** Source-only development milestone; Java compilation and
Minecraft gameplay verification have not been completed.


## v4.3 command-free onboarding
On player join a Hangar Dispatcher villager is spawned nearby if no dispatcher is within 96 blocks. Right-click the villager to access clickable job offers, resume the active work order, or view the mechanic profile. No operator commands are required for this initial gameplay loop. Clickable chat entries still use internal Minecraft commands; a future release should replace them with a dedicated GUI and expand no-command access to every feature. Dispatcher spawning is provisional and does not yet build a safe airport or handle obstructed spawn points.


## v4.4 Command-free onboarding and tablet
- First join creates a nearby Hangar Dispatcher and FAA Training Instructor (villager NPCs).
- First join places a lectern job board on an unobstructed solid surface if available. No terrain is destroyed.
- Right-click FAA Instructor for source-indexed lesson selection.
- Press M to open expanded Mechanic Tablet with Jobs, FAA Academy, Practicals, Study, Tool Truck, Aircraft, Groceries, and Finances.
- Menu interactions still invoke server-side commands internally. Players need not type them.
- These NPCs and board are starter scaffolding, not yet a fully generated airport.
- Source has not been compiled or gameplay tested.

## v4.5: Airport Guide (no player commands)

On joining, a player receives a named **Airport Guide** compass. Right-clicking it
reports the coordinates of the starter airport already generated by `Airfield`.
The airport is not force-loaded, and the guide will explain when it has not yet
been generated. The guide does not automatically point its needle at the airport:
it is a coordinate guide. The existing M-key menu, villager dealers, dispatcher,
and workstations remain available. Commands still exist internally and some
older systems are not yet fully command-free.

The source has not been compiled or tested in a Minecraft client.


## v4.7: Villager Casino prototype
A persistent named casino-host villager appears near the player as a temporary onboarding solution. Right-click the host to buy $25 scratch-off paper tickets or play $20 fictional slots using the existing persistent A&P Craft cash balance. Hold a scratch ticket and right-click to reveal a random payout. All payouts use fictional game currency, and no real money or actual stock markets are involved. Menus are clickable chat text, not a dedicated graphical screen; the casino building is not yet generated.

Scratch-off prize distribution (per 1,000 tickets): 2 × $1,000, 18 × $200, 80 × $50, 250 × $25, 650 × $0. Expected payout $15.85 on a $25 ticket. Slots use three independently drawn symbols, with $100 for triples, $250 for triple diamonds, $15 for pairs, and $0 otherwise.

Source is not compiled or gameplay-tested.

## v4.9 Grocery and kitchen prototype
- A persistent Village Grocer NPC offers six ingredients for fictional currency via right-click and clickable chat links.
- Right-click a smoker with an empty hand to select steak dinner, breakfast plate, or vegetable stew.
- Ingredients are verified and consumed; prepared meals use vanilla edible items with custom display names.
- Normal play does not require typed commands. The menu links execute internal commands.
- This is a prototype: there is no food preparation animation, temperature/doneness system, refrigeration, or actual cooking GUI yet.
- The source has not been compiled or tested in Minecraft. FAA training content is unchanged.

## v5.3 ground vehicle cargo
Newly purchased trucks, SUVs, sedans and wagons use chest-boat placeholders with 27-slot inventory. Motorcycles and performance cars do not. See V5_3_CHANGES.md.

## v5.6 bag usability update
Survival crafting recipes, bag icons, localization, and correct 9/18/27-slot chest screen types. See V5_6_CHANGES.md. Not yet compiled or game-tested.


## v5.8 build reliability
A GitHub Actions Java 17 build workflow and expanded preflight checks have been added. The accidental nested older source tree was removed. See `V5_8_CHANGES.md`. A compiled, tested mod JAR is not yet available.


## v5.9 build validation
See `V5_9_CHANGES.md` for expanded static checks and GitHub Actions build diagnostics. A passing static check does not mean the mod compiles.
