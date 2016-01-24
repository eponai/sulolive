#!/bin/bash -eu

script_dir=$(dirname $0)

cd "$script_dir/.."
lein cljsbuild once test
lein doo phantom test once
