#!/bin/bash -eu

script_dir=$(dirname $0)
foundation_dir="$script_dir/../sulo-style"

foundation_install=$(which foundation)
exit_code=$?
if [ "0" != "$exit_code" ]; then
  echo "Could not find foundation. Installing globally"
  npm install --global foundation-cli
else
  echo "Using foundation: $foundation_install"
fi

cd "$foundation_dir"
npm install

if [[ $# -ge 1 ]]; then
  echo "Calling: foundation $@"
  foundation $@
else
  echo "Calling: foundation build"
  foundation build
fi

