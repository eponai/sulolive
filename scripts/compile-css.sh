#!/bin/bash -eu

script_dir=$(dirname $0)
proj_dir="$script_dir/.."
foundation_dir="$proj_dir/sulo-style"

FOUNDATION="node ../node_modules/foundation-cli/bin/foundation.js" 
BOWER="node_modules/bower/bin/bower"

set +u
if [ -z "$SKIP_INSTALL" ]; then
  npm install
  $BOWER install --allow-root
  ( cd "$foundation_dir" \
    && npm install \
    && "../$BOWER" install --allow-root )
fi
set -u

#######################
## Put bower stuff in resources

assets_dir="resources/public/assets"
css_dir="$assets_dir/css"
flags_dir="$assets_dir/flags"
mkdir -p $css_dir
cp -r bower_components/flag-icon-css/css/flag-icon.min.css "$css_dir/flag-icon.min.css"
mkdir -p "$flags_dir"
cp -r bower_components/flag-icon-css/flags/* "$flags_dir"

######################
## Call foundation with arguments passed to this script (or build)

cd "$foundation_dir"

if [[ $# -ge 1 ]]; then
  echo "Calling: foundation $@"
  $FOUNDATION $@
else
  echo "Calling: foundation build"
  $FOUNDATION build
fi

