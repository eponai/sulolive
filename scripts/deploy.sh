#!/bin/bash -eu

script_dir="$(dirname $0)"
cd "$script_dir/.."

SHA1=$1

# Deploy image to Docker Hub
docker push petterik/jourmoney:$SHA1

# Create new Elastic Beanstalk version
EB_BUCKET=petterik-jourmoney
DOCKERRUN_FILE=$SHA1-Dockerrun.aws.json
sed "s/<TAG>/$SHA1/" < Dockerrun.aws.json.template > $DOCKERRUN_FILE
aws s3 cp $DOCKERRUN_FILE s3://$EB_BUCKET/$DOCKERRUN_FILE
aws elasticbeanstalk create-application-version --application-name petterik-jourmoney \
  --version-label $SHA1 --source-bundle S3Bucket=$EB_BUCKET,S3Key=$DOCKERRUN_FILE

# Update Elastic Beanstalk environment to new version
aws elasticbeanstalk update-environment --environment-name petterik-jourmoney-env \
    --version-label $SHA1

