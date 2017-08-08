#!/bin/bash -eu

script_dir="$(dirname $0)"
cd "$script_dir/.."

SHA1="$1"
DOCKER_IMAGE="$2"
# Take the first 10 chars of the sha1. It should be enough
ASSET_VERSION=$(echo "$SHA1" | cut -c1-10)

# Elastic Beanstalk vars
EB_BUCKET=sulo-elb
EB_APP_NAME=sulo-live
REGION=us-east-1

# Set branch specific variables
if [ "${CIRCLE_BRANCH}" = "production" ]; then
  WEBSERVER_MEMORY=1500
  EB_ENV_NAME="$(scripts/_env-name-of-current-staging.sh $REGION)"
  FIREBASE_SERVICE_ACCOUNT="$FIREBASE_SERVICE_ACCOUNT_PRODUCTION"
elif [ "${CIRCLE_BRANCH}" = "master" ]; then
  WEBSERVER_MEMORY=700
  EB_ENV_NAME="sulo-master"
  FIREBASE_SERVICE_ACCOUNT="$FIREBASE_SERVICE_ACCOUNT_MASTER"
else
  echo "Was not on production or master branch. Was: $CIRCLE_BRANCH"
  exit 1;
fi

echo "Will deploy to environment: $EB_ENV_NAME"

# Create new Elastic Beanstalk version
DOCKERRUN_FILE="$SHA1-Dockerrun.aws.json"
DOCKERRUN_S3_FILE="docker/$EB_APP_NAME/$EB_ENV_NAME/$DOCKERRUN_FILE"

# Create dockerrun file for elastic beanstalk
# Using comma (,) instead of slash (/) in sed because DOCKER_IMAGE contains slashes
cat Dockerrun2.aws.json.template \
    | sed "s,<ASSET_VERSION>,$ASSET_VERSION," \
    | sed "s,<DOCKER_IMAGE>,$DOCKER_IMAGE," \
    | sed "s,<WEBSERVER_MEMORY>,$WEBSERVER_MEMORY," \
    | sed "s,<DATADOG_API_KEY>,$DATADOG_API_KEY," \
    | sed "s,<FIREBASE_SERVICE_ACCOUNT>,$FIREBASE_SERVICE_ACCOUNT," \
    > $DOCKERRUN_FILE

VERSION="$CIRCLE_BRANCH_$SHA1"

aws --region=$REGION s3 cp "$DOCKERRUN_FILE" "s3://$EB_BUCKET/$DOCKERRUN_S3_FILE"
aws --region=$REGION elasticbeanstalk create-application-version --application-name "$EB_APP_NAME" \
  --version-label $VERSION --source-bundle S3Bucket="$EB_BUCKET",S3Key="$DOCKERRUN_S3_FILE"

# Update Elastic Beanstalk environment to new version
aws --region=$REGION elasticbeanstalk update-environment --environment-name "$EB_ENV_NAME" \
    --version-label $VERSION

