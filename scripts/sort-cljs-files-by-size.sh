#!/bin/bash -eu

script_dir=$(dirname $0)
cd "$script_dir/.."
find resources/public/dev/js/out/eponai -type f -exec wc -c \{\} \; | sort

