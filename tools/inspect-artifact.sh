#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
artifact_path=''
expected_sha256=''
while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact)
      [[ $# -ge 2 ]] || { echo '--artifact requires a path' >&2; exit 2; }
      artifact_path="$2"
      shift
      ;;
    --sha256)
      [[ $# -ge 2 ]] || { echo '--sha256 requires a digest' >&2; exit 2; }
      expected_sha256="${2,,}"
      shift
      ;;
    --help|-h)
      echo 'Usage: tools/inspect-artifact.sh [--artifact PATH] [--sha256 DIGEST]'
      exit 0
      ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

version_lines="$(sed -n 's/^mod_version=//p' "$repo_dir/gradle.properties")"
[[ "$(printf '%s\n' "$version_lines" | sed '/^$/d' | wc -l)" == 1 ]] || {
  echo 'Expected exactly one non-empty mod_version entry.' >&2
  exit 1
}
mod_version="$(printf '%s\n' "$version_lines" | sed '/^$/d')"
expected_name="baritonehelper-${mod_version}.jar"
if [[ -z "$artifact_path" ]]; then
  artifact_path="$repo_dir/build/libs/$expected_name"
elif [[ "$artifact_path" != /* ]]; then
  artifact_path="$repo_dir/$artifact_path"
fi
[[ -f "$artifact_path" ]] || { echo "Artifact is missing: $artifact_path" >&2; exit 1; }
[[ "$(basename "$artifact_path")" == "$expected_name" ]] || {
  echo "Artifact name must be $expected_name." >&2
  exit 1
}

for entry in META-INF/neoforge.mods.toml pack.mcmeta \
    dev/dudie/baritonehelper/BaritoneHelper.class assets/baritonehelper/lang/en_us.json; do
  unzip -Z1 "$artifact_path" | grep -Fxq "$entry" || {
    echo "Artifact is missing required entry: $entry" >&2
    exit 1
  }
done
if unzip -Z1 "$artifact_path" | grep -Eq '^dev/dudie/buddybot/'; then
  echo 'Artifact contains removed buddybot production classes.' >&2
  exit 1
fi
metadata="$(unzip -p "$artifact_path" META-INF/neoforge.mods.toml)"
metadata_version="$(printf '%s\n' "$metadata" | sed -n 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
[[ "$metadata_version" == "$mod_version" ]] || {
  echo "Artifact metadata version does not match mod_version ($mod_version): $metadata_version" >&2
  exit 1
}
license="$(printf '%s\n' "$metadata" | sed -n 's/^[[:space:]]*license[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
[[ -n "$license" ]] || { echo 'Artifact metadata does not declare a license.' >&2; exit 1; }
sha256="$(sha256sum "$artifact_path" | awk '{print tolower($1)}')"
if [[ -n "$expected_sha256" && "$sha256" != "$expected_sha256" ]]; then
  echo "Artifact SHA-256 mismatch: expected $expected_sha256, found $sha256." >&2
  exit 1
fi
printf 'artifact=%s\nbytes=%s\nsha256=%s\nmod_version=%s\nmetadata_version=%s\nlicense=%s\nartifact=verified\n' \
  "$artifact_path" "$(stat -c '%s' "$artifact_path")" "$sha256" "$mod_version" "$metadata_version" "$license"
