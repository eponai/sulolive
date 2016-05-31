(ns eponai.client.utils
  (:require [devtools.core :as devtools]
            [eponai.client.logger :as logger]
            [goog.date]
            [cljs-time.coerce :as cljs-time]
            [taoensso.timbre :as timbre :refer-macros [debug]]))

;; ------ Dates -----------
;; Extends equality on goog.date's to speed up shouldComponentUpdate.

(defn date-eq [a b]
  (and (satisfies? cljs-time/ICoerce a)
       (satisfies? cljs-time/ICoerce b)
       (= (cljs-time/to-long a)
          (cljs-time/to-long b))))


(extend-protocol IEquiv
  goog.date.Date
  (-equiv [this other] (date-eq this other))

  goog.date.DateTime
  (-equiv [this other] (date-eq this other))

  goog.date.UtcDateTime
  (-equiv [this other] (date-eq this other)))

;; ------ Util functions -----------

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

;; ------ App initialization -----------

(defn set-level [l]
  (timbre/set-level! l))

(def set-trace #(set-level :trace))
(def set-debug #(set-level :debug))

(defn install-app []
  (enable-console-print!)
  (devtools/enable-feature! :sanity-hints :dirac)
  (devtools/install!)
  (logger/install-logger!))
