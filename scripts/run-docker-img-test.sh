#!/bin/bash -u

HOST=$1
PORT=$2
ENDPOINT=/enter

echo "Test with forwarding (which in production is done by load balancer)"
wget -O- --header "x-forwarded-proto: https" --max-redirect 0 --retry-connrefused -t 30 http://$HOST:$PORT$ENDPOINT

exit_code=$?
if [ "$exit_code" = "0" ]; then
  echo "PASS: We were not redirected"
else
  echo "FAIL: Not exit code 0. Was: $exit_code"
  exit $exit_code
fi

echo "Test with redirect"
wget -O- --max-redirect 0 --retry-connrefused -t 10 http://$HOST:$PORT$ENDPOINT
exit_code=$?

if [ "$exit_code" = "8" ]; then
  echo "PASS: We got redirected and it's fine!"
  exit 0
else
  echo "FAIL: Exit code other than 8, it's not fine. Was: $exit_code"
  exit $exit_code
fi

