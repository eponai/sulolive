#!/bin/bash -eu

script_dir=$(dirname $0)

cd "$script_dir/../firebase"
firebase deploy --only functions
