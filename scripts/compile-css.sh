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

bower_install=$(which bower)
if [ "0" != "$?" ]; then
  echo "Could not find bower. Installing globally"
  npm install -g bower
else
  echo "Using bower: $bower_install"
fi

cd "$foundation_dir"
npm install
bower install

if [[ $# -ge 1 ]]; then
  echo "Calling: foundation $@"
  foundation $@
else
  echo "Calling: foundation build"
  foundation build
fi

