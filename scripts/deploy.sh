#!/bin/bash -eu

script_dir="$(dirname $0)"
cd "$script_dir/.."

SHA1="$1"
DOCKER_IMAGE="$2"

# Deploy image to Docker Hub
docker push $DOCKER_IMAGE 

# Elastic Beanstalk vars
EB_BUCKET=petterik-jourmoney
EB_APP_NAME=petterik-jourmoney
EB_ENV_NAME=petterik-jourmoney-env

# Create new Elastic Beanstalk version
DOCKERRUN_FILE=$SHA1-Dockerrun.aws.json
sed "s/<DOCKER_IMAGE>/$DOCKER_IMAGE/" < Dockerrun.aws.json.template > $DOCKERRUN_FILE
aws s3 cp $DOCKERRUN_FILE s3://$EB_BUCKET/$DOCKERRUN_FILE
aws elasticbeanstalk create-application-version --application-name $EB_APP_NAME \
  --version-label $SHA1 --source-bundle S3Bucket=$EB_BUCKET,S3Key=$DOCKERRUN_FILE

# Update Elastic Beanstalk environment to new version
aws elasticbeanstalk update-environment --environment-name $EB_ENV_NAME \
    --version-label $SHA1

