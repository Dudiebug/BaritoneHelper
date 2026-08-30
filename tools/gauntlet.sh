#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"

java_bin="${BARITONEHELPER_JAVA:-$(command -v java || true)}"
if [[ -z "$java_bin" ]]; then
  echo "Java 21 was not found. Set BARITONEHELPER_JAVA." >&2
  exit 2
fi

java_major="$($java_bin -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
if [[ "$java_major" != "21" ]]; then
  echo "Baritone Helper requires Java 21; found Java ${java_major:-unknown}." >&2
  exit 2
fi

gradle=("$java_bin" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain)
"${gradle[@]}" clean test runGameTestServer build --no-daemon --console=plain

test -s build/libs/baritonehelper-1.0.0.jar
test ! -e src/main/java/dev/dudie/buddybot
test ! -e src/main/resources/assets/buddybot
test ! -e src/main/resources/data/buddybot

if rg -n 'BuddyBotTier|RescueController|FollowOwnerGoal|MeleeAttackGoal|changeDimension\(' \
    src/main/java/dev/dudie/baritonehelper; then
  echo "Removed tier, rescue, combat, or owner-following code is still present." >&2
  exit 1
fi

echo "Baritone Helper gauntlet passed."
