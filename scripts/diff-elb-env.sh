#!/bin/bash -eu

function describe-env {
  env_name=$1
  aws elasticbeanstalk describe-configuration-settings --application-name "sulo-live" --environment-name "$env_name" | grep -C 1 aws:elasticbeanstalk:application:environment
}

echo "Diffing sulo-pink and sulo-purple. No output means no diff."

diff <(describe-env "sulo-pink") <(describe-env "sulo-purple")
