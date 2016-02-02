#!/bin/bash -eu

script_dir=$(dirname $0)
cd "$script_dir/.."

export TIMBRE_LEVEL=":info"
export PORT="8080"
export CLJS_BUILD_ID="release"

lein repl
