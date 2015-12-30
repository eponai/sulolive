(ns eponai.client.ui
  (:require [clojure.string :as s]))

(defn unique-str [uuid v]
  (if (vector? v)
    (s/join "-" (concat ["uniq" uuid] v))
    (do
      (prn "WARN: The value of :key was not a vector. Value: " v)
      v)))
