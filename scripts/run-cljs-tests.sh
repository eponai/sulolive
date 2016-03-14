#!/bin/bash -eu

script_dir=$(dirname $0)

cd "$script_dir/.."
lein run-tests-web
