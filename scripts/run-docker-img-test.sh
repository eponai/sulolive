#!/bin/bash -eu

PORT=$1

wget -O- --max-redirect 0 --retry-connrefused -t 10 http://localhost:$PORT
exit_code=$?

if [ "$exit_code" = "8" ]; then
  echo "We got redirected and it's fine!"
else
  echo "Exit code other than 8, it's not fine. Was: $exit_code"
fi

