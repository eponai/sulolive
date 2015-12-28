(ns eponai.client.format
  (:require [cljs-time.core :as t]
            [cljs-time.format :as t.format]
            [cljs.reader :as reader]
            [goog.date.DateTime]))

(def ymd-formatter (t.format/formatter "yyyy-MM-dd"))

(defn date->ymd-str [js-date]
  (let [date (doto (goog.date.DateTime.) (.setTime (.getTime js-date)))]
    (t.format/unparse ymd-formatter date)))

(defn input->date [ymd-str]
  (let [date (t.format/parse ymd-formatter ymd-str)]
    {:date/ymd   ymd-str
     :date/day   (t/day date)
     :date/month (t/month date)
     :date/year  (t/year date)}))

(defn input->currency [currency]
  {:currency/code currency})

(defn input->tags [tags]
  (map #(hash-map :tag/name %) tags))

(defn input->transaction [amount title description uuid created-at]
  {:transaction/amount     (reader/read-string amount)
   :transaction/name       title
   :transaction/details    description
   :transaction/uuid       uuid
   :transaction/created-at created-at})
