#!/bin/bash -eu

script_dir=$(dirname $0)
cd "$script_dir/.."

(echo "(core/main-release-no-ssl)"; cat <&0) | lein repl
