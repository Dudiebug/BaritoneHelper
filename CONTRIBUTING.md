# Contributing

Use Java 21 and NeoForge 21.1.248. Keep collector behavior server-authoritative
and preserve the relocated Baritone LGPL headers when changing vendored files.

Before opening a pull request, run:

```sh
./gradlew clean test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Do not add combat, following, rescue, idle wandering, direct predicted-drop
collection, direct world placement, or unbounded scans. Changes to the payload
schema must increment the protocol version and include stale-revision tests.
