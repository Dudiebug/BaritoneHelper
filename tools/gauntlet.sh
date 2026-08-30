#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"

java_bin="${BUDDYBOT_JAVA:-}"
if [[ -z "$java_bin" ]]; then
  java_bin="$(command -v java || true)"
fi
if [[ -z "$java_bin" && -x /home/dudie/.cache/codex-java21/bin/java ]]; then
  java_bin=/home/dudie/.cache/codex-java21/bin/java
fi
if [[ -z "$java_bin" ]]; then
  echo "Java 21 was not found. Set BUDDYBOT_JAVA." >&2
  exit 2
fi

java_major="$($java_bin -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
if [[ "$java_major" != "21" ]]; then
  echo "BuddyBot requires Java 21; found Java ${java_major:-unknown}." >&2
  exit 2
fi

gradle=("$java_bin" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain)
"${gradle[@]}" clean test build --no-daemon --console=plain

if rg -n 'LivingDamageEvent|LivingIncomingDamageEvent|setInvulnerable|setCanceled\(true\)' src/main/java; then
  echo "Forbidden damage cancellation or invulnerability hook found." >&2
  exit 1
fi

if ! rg -q 'return !\(target instanceof Player\)' src/main/java/dev/dudie/buddybot/entity/BuddyBotEntity.java; then
  echo "Explicit player-target rejection is missing." >&2
  exit 1
fi

skin=src/main/resources/assets/buddybot/textures/entity/buddy_bot.png
file "$skin" | rg -q 'PNG image data, 64 x 64,.*RGBA'
test -s build/libs/buddybot-1.0.0.jar

echo "BuddyBot gauntlet passed."
