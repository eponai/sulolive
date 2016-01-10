#!/bin/bash -eu

script_dir=$(dirname $0)

cd "$script_dir/.."
lein cljsbuild once test
node resources/public/test/js/out/budget.js

