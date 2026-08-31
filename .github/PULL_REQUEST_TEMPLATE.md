## Summary

Describe what changed and why.

## User-visible behavior

Describe any player/server behavior change. Write `None` for internal-only changes.

## Risk areas

Check every area affected by this PR:

- [ ] Worker lifecycle / ownership
- [ ] Persistence / migration
- [ ] Inventory / storage / item conservation
- [ ] World scanning / chunk tickets
- [ ] Baritone-derived pathing / movement
- [ ] Mining / placement / interaction
- [ ] Networking / controller protocol
- [ ] UI / translations / assets
- [ ] Build / release / documentation only

## Verification

- [ ] `./gradlew clean test --no-daemon --console=plain`
- [ ] `./gradlew runGameTestServer --no-daemon --console=plain`
- [ ] `./gradlew build --no-daemon --console=plain`
- [ ] Added or updated regression coverage where behavior changed
- [ ] Updated `CHANGELOG.md` for user-facing changes
- [ ] Updated documentation when the workflow or architecture changed

## Licensing

- [ ] I preserved applicable LGPL headers/attribution in Baritone-derived files.
- [ ] New third-party material includes the required license/credit information.
