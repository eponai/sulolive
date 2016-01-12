(ns eponai.common.generators
  (:require [clojure.test.check.generators :as gen]))

(defn gen-amount []
  (gen/fmap str gen/int))

(defn gen-currency []
  (gen/fmap (fn [s] (apply str (take 3 s)))
            (gen/vector gen/char-alpha)))

(defn gen-title []
  gen/string-alphanumeric)

(defn gen-date []
  (gen/fmap (fn [[y m d]] (str y "-" m "-" d))
            (gen/tuple
              (gen/choose 1000 9999)
              (gen/choose 10 12)
              (gen/choose 10 28))))

(defn gen-tags []
  (gen/fmap set (gen/vector gen/string-alphanumeric)))

(defn gen-transaction []
  (gen/hash-map :input-amount (gen-amount)
                :input-currency (gen-currency)
                :input-title (gen-title)
                :input-date (gen-date)
                :input-description gen/string-alphanumeric
                :input-tags (gen-tags)
                :input-created-at gen/pos-int
                :input-uuid gen/uuid
                :input-budget gen/uuid))