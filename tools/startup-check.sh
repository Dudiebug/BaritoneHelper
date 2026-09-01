#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
artifact_path=''
timeout_seconds=60
ready_pattern="${BARITONEHELPER_CANDIDATE_READY_PATTERN:-}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact)
      [[ $# -ge 2 ]] || { echo '--artifact requires a path' >&2; exit 2; }
      artifact_path="$2"
      shift
      ;;
    --timeout)
      [[ $# -ge 2 ]] || { echo '--timeout requires seconds' >&2; exit 2; }
      timeout_seconds="$2"
      shift
      ;;
    --ready-pattern)
      [[ $# -ge 2 ]] || { echo '--ready-pattern requires a regex' >&2; exit 2; }
      ready_pattern="$2"
      shift
      ;;
    --help|-h)
      echo 'Usage: tools/startup-check.sh --artifact PATH --ready-pattern REGEX'
      exit 0
      ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

[[ -n "${BARITONEHELPER_CANDIDATE_STARTUP_COMMAND:-}" ]] || {
  echo 'UNVERIFIED: set BARITONEHELPER_CANDIDATE_STARTUP_COMMAND to an exact-artifact launcher.' >&2
  exit 2
}
[[ -n "$ready_pattern" ]] || {
  echo 'UNVERIFIED: set BARITONEHELPER_CANDIDATE_READY_PATTERN or pass --ready-pattern.' >&2
  exit 2
}
[[ "$timeout_seconds" =~ ^[1-9][0-9]*$ ]] || { echo 'timeout must be a positive integer' >&2; exit 2; }

"$repo_dir/tools/inspect-artifact.sh" --artifact "$artifact_path" >/dev/null
if [[ -z "$artifact_path" ]]; then
  mod_version="$(sed -n 's/^mod_version=//p' "$repo_dir/gradle.properties")"
  artifact_path="$repo_dir/build/libs/baritonehelper-${mod_version}.jar"
elif [[ "$artifact_path" != /* ]]; then
  artifact_path="$repo_dir/$artifact_path"
fi
artifact_path="$(cd "$(dirname "$artifact_path")" && pwd)/$(basename "$artifact_path")"

read -r -a command_parts <<< "${BARITONEHELPER_CANDIDATE_STARTUP_COMMAND}"
if [[ -n "${BARITONEHELPER_CANDIDATE_STARTUP_ARGUMENTS:-}" ]]; then
  read -r -a extra_parts <<< "${BARITONEHELPER_CANDIDATE_STARTUP_ARGUMENTS}"
  command_parts+=("${extra_parts[@]}")
fi
for index in "${!command_parts[@]}"; do
  command_parts[$index]="${command_parts[$index]//\{artifact\}/$artifact_path}"
done

log_dir="$(mktemp -d "${TMPDIR:-/tmp}/baritonehelper-startup.XXXXXX")"
stdout_log="$log_dir/stdout.log"
stderr_log="$log_dir/stderr.log"
export BARITONEHELPER_CANDIDATE_ARTIFACT="$artifact_path"
export BARITONEHELPER_STARTUP_CHECK=1
"${command_parts[@]}" >"$stdout_log" 2>"$stderr_log" &
pid=$!
deadline=$((SECONDS + timeout_seconds))
ready=0
while (( SECONDS < deadline )); do
  if grep -Eq "$ready_pattern" "$stdout_log" "$stderr_log" 2>/dev/null; then
    ready=1
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    break
  fi
  sleep 1
done
if (( ready == 0 )); then
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  echo "UNVERIFIED: launcher did not emit ready marker '$ready_pattern'. Logs: $log_dir" >&2
  exit 1
fi
if kill -0 "$pid" 2>/dev/null; then
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  echo "UNVERIFIED: launcher did not perform a clean shutdown within $timeout_seconds seconds. Logs: $log_dir" >&2
  exit 1
fi
if wait "$pid"; then
  status=0
else
  status=$?
fi
if (( status != 0 )); then
  echo "Candidate launcher exited $status after ready marker. Logs: $log_dir" >&2
  exit "$status"
fi
rm -rf "$log_dir"
echo "Candidate artifact startup and clean-shutdown check passed: $artifact_path"
