#!/bin/bash -u

which react-native
exit=$?
if [[ "$exit" != "0" ]]; then
  echo "Install react-native cli with:"
  echo "pm install -g react-native-cli"
  exit 1
fi

set -e

lein clean
re-natal use-ios-device real
react-native run-ios

echo "Now press Run in Xcode to run the app with figwheel"
echo " on your device"

rlwrap lein figwheel-ios

