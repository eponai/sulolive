#!/bin/bash -eu

script_dir=$(dirname $0)
cd "$script_dir/.."

# export TIMBRE_LEVEL=":info"
export PORT="3000"
export CLJS_BUILD_ID="release"

(echo "(core/main-release-no-ssl)"; cat <&0) | lein repl
