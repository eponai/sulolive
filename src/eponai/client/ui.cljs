(ns eponai.client.ui
  (:require [clojure.string :as s]))

(defn unique-str [uuid v]
  (if (vector? v)
    (s/join "-" (concat ["uniq" uuid] v))
    (do
      (prn "WARN: The value of :key was not a vector. Value: " v)
      v)))

(defn assoc-if [bool m k v-or-f]
  (if bool
    (assoc m k (if (fn? v-or-f)
                 (v-or-f)
                 v-or-f))
    m))
