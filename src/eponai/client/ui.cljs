(ns eponai.client.ui
  (:require [clojure.string :as s]))

(defn unique-str [uuid v]
  (if (vector? v)
    (s/join "-" (concat ["uniq" uuid] v))
    v))
