#!/bin/bash -eu

script_dir=$(dirname $0)
cd "$script_dir/.."

# Greps for case-insensitive xxx 

found_xxx=$(find src test -type f -exec grep -Hi 'xxx' \{\} \;)

if [[ "$found_xxx" = "" ]]; then
  exit 0
else
  echo "Aborting pre-commit. Found XXX in your code:" 
  echo "$found_xxx"
  exit 1
fi
