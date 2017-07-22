#!/bin/bash -eux

HOST=$1
PORT=$2
ENDPOINT=/

echo "Test with forwarding (which in production is done by load balancer)"
docker run --network container:contacts appropriate/curl -H 'x-forwarded-proto: https' --retry 30 --retry-delay 1 --retry-connrefused http://$HOST:$PORT$ENDPOINT

