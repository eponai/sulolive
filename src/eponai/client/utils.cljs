(ns eponai.client.utils
  (:require [devtools.core :as devtools]
            [eponai.client.logger :as logger]
            [taoensso.timbre :as timbre :refer-macros [debug]]))

(defn distinct-by
  "Distinct by (f input). See clojure.core/distinct."
  ([f]
   (fn [rf]
     (let [seen (volatile! #{})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (let [by (f input)]
            (if (contains? @seen by)
              result
              (do (vswap! seen conj by)
                  (rf result input)))))))))
  ([f coll]
   (sequence (distinct-by f) coll)))

(defn set-level [l]
  (timbre/set-level! l))

(def set-trace #(set-level :trace))
(def set-debug #(set-level :debug))

(defn install-app []
  (enable-console-print!)
  (devtools/enable-feature! :sanity-hints :dirac)
  (devtools/install!)
  (logger/install-logger!))
