#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$script_dir/dist"

case "$(go env GOOS)" in
  windows) output="$script_dir/dist/hello_native.dll" ;;
  darwin) output="$script_dir/dist/libhello_native.dylib" ;;
  *) output="$script_dir/dist/libhello_native.so" ;;
esac

(cd "$script_dir" && go build -buildmode=c-shared -o "$output" .)
echo "$output"
