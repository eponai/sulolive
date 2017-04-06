#!/bin/bash

script_dir=$(dirname $0)

export TIMBRE_LEVEL=":info"
export SESSION_COOKIE_STORE_KEY="a 16 bits secret"
export SESSION_COOKIE_name="cookie-name"
export CLJS_BUILD_ID=release 
export SERVER_URL_SCHEMA="http"
export SERVER_URL_HOST="localhost:3000"

cd "$script_dir"
lein uberjar
java -server -Xmx4g -Dclojure.compiler.direct-linking=true -cp ./target/uberjar/budget-0.1.0-SNAPSHOT-standalone.jar eponai.server.core
