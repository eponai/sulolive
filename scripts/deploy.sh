#!/bin/bash -eux

script_dir="$(dirname $0)"
cd "$script_dir/.."

SHA1="$1"
DOCKER_IMAGE="$2"
# Take the first 10 chars of the sha1. It should be enough
ASSET_VERSION=$(echo "$SHA1" | cut -c1-10)

# Deploy image to Docker Hub
docker push $DOCKER_IMAGE 

function env_name_of_current_staging {
  staging_url="sulo-stage.us-east-1.elasticbeanstalk.com"

  aws --output text elasticbeanstalk describe-environments | \
    grep "$staging_url" | \
    grep -o 'sulo-blue\s\|sulo-green\s\|sulo-purple\s\|sulo-pink\s' | \
    tr -d '[[:blank:]]'
}

# Elastic Beanstalk vars
EB_BUCKET=sulo-elb
EB_APP_NAME=sulo-live
EB_ENV_NAME="$(env_name_of_current_staging)"
REGION=us-east-1

echo "Will deploy to environment: $EB_ENV_NAME"

# Create new Elastic Beanstalk version
DOCKERRUN_FILE="$SHA1-Dockerrun.aws.json"
DOCKERRUN_S3_FILE="docker/$EB_APP_NAME/$EB_ENV_NAME/$DOCKERRUN_FILE"

# Create dockerrun file for elastic beanstalk
# Using comma (,) instead of slash (/) in sed because DOCKER_IMAGE contains slashes
cat Dockerrun2.aws.json.template | sed "s,<ASSET_VERSION>,$ASSET_VERSION," | sed  "s,<DOCKER_IMAGE>,$DOCKER_IMAGE," | sed  "s,<DATADOG_API_KEY>,$DATADOG_API_KEY," > $DOCKERRUN_FILE

aws --region=$REGION s3 cp "$DOCKERRUN_FILE" "s3://$EB_BUCKET/$DOCKERRUN_S3_FILE"
aws --region=$REGION elasticbeanstalk create-application-version --application-name "$EB_APP_NAME" \
  --version-label $SHA1 --source-bundle S3Bucket="$EB_BUCKET",S3Key="$DOCKERRUN_S3_FILE"

# Update Elastic Beanstalk environment to new version
aws --region=$REGION elasticbeanstalk update-environment --environment-name "$EB_ENV_NAME" \
    --version-label $SHA1

