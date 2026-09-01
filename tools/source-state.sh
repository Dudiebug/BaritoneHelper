#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
require_clean=0
output_file=''

while [[ $# -gt 0 ]]; do
  case "$1" in
    --require-clean) require_clean=1 ;;
    --output)
      [[ $# -ge 2 ]] || { echo "--output requires a path" >&2; exit 2; }
      output_file="$2"
      shift
      ;;
    --help|-h)
      echo "Usage: tools/source-state.sh [--require-clean] [--output PATH]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
  shift
done

head="$(git -C "$repo_dir" rev-parse HEAD)"
branch="$(git -C "$repo_dir" branch --show-current)"
[[ -n "$branch" ]] || branch='(detached)'
status="$(git -C "$repo_dir" status --porcelain=v1 --untracked-files=all)"
version="$(sed -n 's/^mod_version=//p' "$repo_dir/gradle.properties")"
[[ "$(printf '%s\n' "$version" | sed '/^$/d' | wc -l)" == 1 ]] || {
  echo 'Expected exactly one non-empty mod_version entry.' >&2
  exit 1
}

if (( require_clean )) && [[ -n "$status" ]]; then
  echo "Source state is dirty; refusing clean verification." >&2
  printf '%s\n' "$status" >&2
  exit 1
fi

clean='true'
[[ -n "$status" ]] && clean='false'
rendered="repository=$repo_dir
branch=$branch
commit=$head
mod_version=$version
clean=$clean"
if [[ -n "$status" ]]; then
  rendered+=$'\nstatus:\n'
  rendered+="$status"
fi

if [[ -n "$output_file" ]]; then
  if [[ "$output_file" = /* ]]; then
    output_path="$output_file"
  else
    output_path="$repo_dir/$output_file"
  fi
  mkdir -p "$(dirname "$output_path")"
  printf '%s\n' "$rendered" > "$output_path"
fi
printf '%s\n' "$rendered"
