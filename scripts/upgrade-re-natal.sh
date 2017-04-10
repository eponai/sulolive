#!/bin/bash -eu

script_dir=$(dirname $0)
cd "$script_dir/.."

# Updating re-natal

## I don't know why, but this never works:
## npm upgrade -g re-natal

npm install re-natal@latest -g

echo "Now using re-natal version:"
re-natal --version

