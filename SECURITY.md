# Security policy

Security fixes are targeted at the current release line. If possible, reproduce a report against the latest Baritone Helper release before submitting it.

## Reporting a vulnerability

Do not open a public issue for a vulnerability that could enable abuse, unauthorized control, duplication, unsafe world modification, or denial of service.

Use GitHub's private vulnerability reporting flow from the repository **Security** tab when **Report a vulnerability** is available. If that option is unavailable, contact the repository maintainer privately through GitHub rather than publishing exploit details in an issue.

Include:

- affected Baritone Helper version;
- Minecraft and NeoForge versions;
- dedicated-server or singleplayer context;
- reproduction steps;
- expected and observed behavior;
- relevant logs, packet traces, crash reports, or minimal test worlds;
- whether the worker or world was migrated from an older release.

## High-priority report classes

Dashboard and worker actions are server-authoritative: ownership, entity identity, dimension, registry IDs, numeric bounds, and configuration revisions are validated on the server. Reports involving any of the following are high priority:

- cross-owner worker control or inventory access;
- authorization or revision-check bypasses;
- item duplication or cargo loss caused by a security boundary failure;
- persistent or unbounded chunk-ticket leaks;
- unsafe block modification outside configured policy/boundaries;
- malformed network payloads that crash or materially degrade the server;
- stale asynchronous work that can revive cancelled or replaced actions.

Please avoid destructive testing against servers or worlds you do not own or have explicit permission to test.
