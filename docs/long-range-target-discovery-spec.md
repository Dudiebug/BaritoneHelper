# Long-range target discovery executable specification

Status: autonomous run; human approval was not obtained before implementation.

## Contract

The v2.0.0 worker keeps the configured work-area boundary authoritative while
making discovery of matching blocks independent of the worker's current line
of sight. A frontier chunk that is inside the configured horizontal radius is
requested, observed until loaded, scanned within the configured vertical
radius, and released when it is no longer needed. No frontier chunk is
silently discarded because it was initially unloaded.

## Scenarios

1. A matching block 4, 16, 32, 64, and 128 blocks from a worker is found when
   the configured horizontal radius contains it.
2. A matching block in an initially unloaded chunk inside the radius is found,
   its search ticket is bounded and released after the scan, and the worker
   can path to and collect it.
3. A matching block behind a corner, behind a wall with an opening, underground,
   or several Y levels above/below the worker is accepted for path evaluation
   even when the worker's current-eye raycast cannot see it.
4. A matching block outside the horizontal or vertical radius remains untouched.
5. A matching block inside a `NO_MODIFY` or `NO_ENTER` zone remains untouched.
6. Multiple matching blocks are considered without restarting the frontier at
   chunk zero after each collection; a reachable candidate can beat a closer
   unreachable candidate.
7. Stopping, restarting, dismissing, changing the target/work area, changing
   dimension, and owner logout do not leak search tickets or permanently lose
   the active frontier.
8. Existing progressive breaking, drops, storage, no-work-zone, placement,
   parkour, bridging, water, offline, inventory, dismissal, protection, GUI,
   and network behavior remains green.

## Invariants and budgets

- Search is incremental and never scans a cubic radius synchronously.
- Search frontier tickets have a separate internal reason from worker/entity
  and route/target tickets and remain within the entity's ticket ceiling.
- The default search budget remains bounded at 4096 block positions per worker
  tick unless measurement requires a smaller safe value.
- Candidate interaction stances are evaluated from their hypothetical eye
  positions; current worker-eye LOS is not a discovery prerequisite.
- A failed Baritone calculation immediately frees the controller to reject the
  candidate temporarily and try another candidate.
- Cached candidates are revalidated before use and individual collections do
  not clear the entire active search state.

## Test plan

- Add GameTests for the distance, unloaded-chunk, occluded, underground,
  vertical, radius-boundary, no-work-zone, multiple-target, restart, offline,
  acceptance-sequence, and ticket-lifecycle scenarios above.
- Add JUnit/source contracts for the frontier state machine, hypothetical-eye
  LOS implementation, bounded ticket budget, and path-failure state exposure
  where those behaviors can be checked without a live server.
- Run the full project gates on Java 21:
  `./gradlew clean test --no-daemon --console=plain`,
  `./gradlew runGameTestServer --no-daemon --console=plain`, and
  `./gradlew build --no-daemon --console=plain`.

## Setup and authorization

- Repository: clone `https://github.com/Dudiebug/BaritoneHelper`, branch
  `fix/long-range-target-discovery`, preserve tag `v2.0.0`.
- No new runtime or test dependency is authorized; reuse NeoForge, the
  embedded Baritone engine, JUnit, and the existing GameTest harness.
- Logical checkpoint commits are part of the user's requested workflow.
- Push/PR creation and the requested Discord notification are attempted only
  after local verification and only if the available credentials permit them.
