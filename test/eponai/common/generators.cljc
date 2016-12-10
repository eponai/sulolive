(ns eponai.common.generators
  (:require
    [clojure.test.check.generators :as gen]
    #?(:clj [clj-time.coerce :as coerce]
       :cljs [cljs-time.coerce :as coerce])
    #?(:clj [clj-time.core :as time]
       :cljs [cljs-time.core :as time])))

(defn gen-amount []
  (gen/fmap str gen/pos-int))

(defn gen-title []
  gen/string-alphanumeric)

(defn gen-date []
  (gen/fmap (fn [[y m d]] {:date/ymd       (str y "-" m "-" d)
                           :date/timestamp (coerce/to-long (time/date-time y m d))})
            (gen/tuple
              (gen/choose 1000 9999)
              (gen/choose 10 12)
              (gen/choose 10 28))))

(defn gen-tags []
  (gen/fmap set (gen/not-empty (gen/vector (gen/hash-map :tag/name gen/string-alphanumeric)))))

(defn gen-project []
  (gen/hash-map :project/uuid gen/uuid :project/created-at gen/pos-int))

(defn gen-transaction
  ([] (gen-transaction (gen-project)))
  ([project-generator]
   (gen/hash-map :transaction/amount (gen-amount)
                 :transaction/title (gen-title)
                 :transaction/date (gen-date)
                 :transaction/tags (gen-tags)
                 :transaction/created-at gen/pos-int
                 :transaction/uuid gen/uuid
                 :transaction/project project-generator
                 :transaction/type (gen/elements [:transaction.type/expense :transaction.type/income]))))
