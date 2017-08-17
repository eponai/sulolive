#!/bin/bash -e

script_dir=$(dirname $0)

if [ -z "$1" ]; then
  echo "No project was passed. Defaulting to our dev project."
  project=leafy-firmament-160421
else
  project="$1"
fi


cd "$script_dir/../firebase"

echo "Deploying functions to project: $project"
firebase --project "$project" deploy --only functions
