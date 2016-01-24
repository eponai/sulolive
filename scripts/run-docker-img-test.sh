#!/bin/bash -u

HOST=$1
PORT=$2

wget -O- --max-redirect 0 --retry-connrefused -t 1 http://$HOST:$PORT
exit_code=$?

if [ "$exit_code" = "8" ]; then
  echo "We got redirected and it's fine!"
  exit 0
elif [ "$exit_code" = "0" ]; then
 exit 0 
fi

echo "Exit code other than 0 or 8, it's not fine. Was: $exit_code"
exit $exit_code

