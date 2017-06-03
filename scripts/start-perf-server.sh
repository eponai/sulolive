#!/bin/bash -eu

is_dev_system=$(grep -A 1 "defn -main" src/eponai/server/core.clj | grep "system/dev-system")


if [ "" = "$is_dev_system" ]; then
  echo "edit eponai.server.core under function -main:"
  echo "-  (let [system (component/start-system (make-system {::system-fn system/prod-system}))"
  echo "+  (let [system (component/start-system (make-system {::system-fn system/dev-system}))"
  echo "before running perf tests."
  exit 1
fi

echo "eponai.server.core is using dev-system, running perf test."

export TIMBRE_LEVEL=":info"
export JAVA_OPTS="-Xmx4g"
lein uberjar

java -server -Dclojure.compiler.direct-linking=true -cp target/uberjar/budget-0.1.0-SNAPSHOT-standalone.jar eponai.server.core
