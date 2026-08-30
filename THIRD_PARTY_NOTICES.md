# Third-party notices

## Baritone-derived server engine

The files under `src/main/java/dev/dudie/baritonehelper/internal/baritone/`
are a minimal, relocated derivative of Baritone server pathing and entity
interaction code. They retain the upstream LGPL headers and are distributed
under LGPL-3.0-or-later. The implementation was adapted for NeoForge 21.1.248
from these public references:

- Goodbird-git/PlayerEngine, branch `1.21.1-arch`, commit
  `aa4ad2d5ec2a834107c76cdb58af732acb192ecc`;
- Cabaletta Baritone, branch `1.21.1`, inspected at commit
  `5f259b7f1ffaa8dca4cd1207c34bb8fb5e534756`;
- Ladysnake/Automatone swimming work, commit
  `1fb7ad155cf4ca7fc846506235da03d6fae4c0e4`.

The vendored subset excludes commands, combat, following, farming, scripting,
LLM integrations, client-only UI, and unrelated application features. It is
relocated to avoid package collisions with separately installed Baritone. The
corresponding source is included in this repository and the Gradle `sourcesJar`
artifact. LGPL terms are in `LICENSES/LGPL-3.0.txt`.

## NeoForge and Minecraft

Baritone Helper targets NeoForge 21.1.248 and Minecraft 1.21.1. Those upstream
projects retain their own licenses and notices in their distributions; this
repository does not relicense them.

## Project code

Code authored for Baritone Helper outside the vendored directory is released
under the MIT License in `LICENSE`. Gradle metadata intentionally declares both
MIT and LGPL-3.0-or-later.
