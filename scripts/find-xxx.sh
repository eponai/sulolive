#!/bin/bash -eu

script_dir=$(dirname $0)

# Change directory to project dir. Can be run as a git hook.
if [[ "$script_dir" = ".git/hooks" ]]; then
  cd "$script_dir/../.."
else
  cd "$script_dir/.."
fi 

# Greps for case-insensitive xxx 

found_xxx=$(find src test -type f -exec grep -Hi 'xxx' \{\} \;)

if [[ "$found_xxx" = "" ]]; then
  exit 0
else
  echo "Aborting pre-commit. Found XXX in your code:" 
  echo "$found_xxx"
  exit 1
fi
