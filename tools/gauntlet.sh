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

mapfile -t runtime_jars < <(find build/libs -maxdepth 1 -type f -name 'baritonehelper-*.jar' ! -name '*-sources.jar' -print)
mapfile -t source_jars < <(find build/libs -maxdepth 1 -type f -name 'baritonehelper-*-sources.jar' -print)
if [[ "${#runtime_jars[@]}" != 1 || "${#source_jars[@]}" != 1 ]]; then
  echo "Expected exactly one runtime JAR and one source JAR." >&2
  exit 1
fi
mod_version="$(sed -n 's/^mod_version=//p' gradle.properties)"
runtime_jar="build/libs/baritonehelper-${mod_version}.jar"
source_jar="build/libs/baritonehelper-${mod_version}-sources.jar"
test "${runtime_jars[0]}" = "$runtime_jar"
test "${source_jars[0]}" = "$source_jar"
test -s "$runtime_jar"
test -s "$source_jar"
test ! -e src/main/java/dev/dudie/buddybot
test ! -e src/main/resources/data/buddybot
test -f src/main/resources/assets/buddybot/models/item/buddy_bot.json

if rg -n 'BuddyBotTier|RescueController|FollowOwnerGoal|MeleeAttackGoal|FloatGoal|changeDimension\(' \
    src/main/java/dev/dudie/baritonehelper -g '!**/gametest/**'; then
  echo "Removed tier, rescue, combat, following, or idle AI code is still present." >&2
  exit 1
fi

echo "Baritone Helper gauntlet passed."
