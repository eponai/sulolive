#!/bin/bash -u

HOST=$1
PORT=$2

echo "This test is disabled until we get ssl certificate for sulo.live"
exit 0

echo "Test with forwarding (which in production is done by load balancer)"
wget -O- --user=sulo --password=hejsan --header "x-forwarded-proto: https" --max-redirect 0 --retry-connrefused -t 30 http://$HOST:$PORT

exit_code=$?
if [ "$exit_code" = "0" ]; then
  echo "PASS: We were not redirected"
else
  echo "FAIL: Not exit code 0. Was: $exit_code"
  exit $exit_code
fi

echo "Test with redirect"
wget -O- --user=sulo --password=hejsan --max-redirect 0 --retry-connrefused -t 10 http://$HOST:$PORT
exit_code=$?

if [ "$exit_code" = "8" ]; then
  echo "PASS: We got redirected and it's fine!"
  exit 0
else
  echo "FAIL: Exit code other than 8, it's not fine. Was: $exit_code"
  exit $exit_code
fi

