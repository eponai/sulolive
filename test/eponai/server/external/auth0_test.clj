(ns eponai.server.external.auth0-test
  (:require
    [eponai.server.external.auth0 :as auth0]
    [clojure.test :refer [deftest is are]]
    [clj-time.core :as time])
  (:import (org.joda.time DateTime)))

(deftest refreshing-tokens
  (let [now (time/now)]
    (are [outcome add-secs]
      (= outcome
         (auth0/token-expiring-within?
           {:exp (-> (.getMillis ^DateTime now) (+ (* add-secs 1000)) (quot 1000))}
           (time/hours 1)
           now))
      false -1
      true 1
      true 3599
      false 3601)))
