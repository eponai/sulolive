#!/bin/bash -eu

script_dir=$(dirname $0)
foundation_dir="$script_dir/../resources/public/lookandfeel/jourmoney-style"

foundation_install=$(which foundation)
exit_code=$?
if [ "0" != "$exit_code" ]; then
  echo "Could not find foundation. Installing globally"
  npm install --global foundation-cli
else
  echo "Using foundation: $foundation_install"
fi

(cd $foundation_dir && \
 npm install && \
 foundation build)

