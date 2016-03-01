(ns eponai.common.generators
  (:require [clojure.test.check.generators :as gen]))

(defn gen-amount []
  (gen/fmap str gen/int))

(defn gen-currency []
  (gen/fmap (fn [s] (apply str (take 3 (cycle s))))
            (gen/not-empty (gen/vector gen/char-alpha))))

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
  (gen/hash-map :transaction/amount (gen-amount)
                :transaction/currency (gen-currency)
                :transaction/title (gen-title)
                :transaction/date (gen-date)
                :transaction/tags (gen-tags)
                :transaction/created-at gen/pos-int
                :transaction/uuid gen/uuid
                :transaction/budget gen/uuid
                :transaction/type (gen/elements [:transaction.type/expense :transaction.type/income])))
