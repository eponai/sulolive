(ns eponai.server.datomic.validate
  (:require [eponai.common.validate :as v]))

(defn valid-signup?
  "Validate the signup parameters. Checks that username and password are not empty,
  and that the password matches the repeated password. Throws an ExceptionInfo if validation fails."
  [user]
  (v/validate "empty user email" not-empty (:username user)))

(defn valid-date?
  "Validate the get params for user/txs. Validating that d m y params are numbers."
  [params]
  (let [{:keys [d m y]} params]
    (v/validate "date params not numbers" every? #(or (nil? %) (Long/parseLong %)) [d m y])))
