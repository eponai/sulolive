#!/bin/bash -eu

## Finds the environment name for the current
## elastic beanstalk environment that's running
## our stage environment.

REGION="$1"

staging_url="sulo-stage.us-east-1.elasticbeanstalk.com"

aws --region=$REGION --output text elasticbeanstalk describe-environments | \
  grep "$staging_url" | \
  grep -o 'sulo-purple\s\|sulo-pink\s' | \
  tr -d '[[:blank:]]'
