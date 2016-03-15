#!/bin/bash -eu

script_dir=$(dirname $0)
cd "$script_dir/.."

npm install -g re-natal

re-natal deps

re-natal xcode

re-natal use-figwheel
