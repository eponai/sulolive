(ns eponai.fullstack.utils
  (:require [clojure.core.async :as async]))

(defn take-with-timeout [chan label & [timeout-millis]]
  {:pre [(string? label)]}
  (let [[v c] (async/alts!! [chan (async/timeout (or timeout-millis 5000))])]
    (when-not (= c chan)
      (throw (ex-info "client login timed out" {:where (str "awaiting " label)})))
    v))

(defn multiply-chan [chan n]
  (let [chan-mult (async/mult chan)
        readers (repeatedly n #(async/chan))]
    (doseq [r readers]
      (async/tap chan-mult r))
    readers))
