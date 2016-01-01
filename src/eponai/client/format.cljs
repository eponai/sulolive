(ns eponai.client.format
  (:require [cljs-time.core :as t]
            [cljs-time.format :as t.format]
            [cljs.reader :as reader]
            [goog.date.DateTime]))

(def ymd-formatter (t.format/formatter "yyyy-MM-dd"))

(defn date->ymd-str [js-date]
  (let [date (doto (goog.date.DateTime.) (.setTime (.getTime js-date)))]
    (t.format/unparse ymd-formatter date)))

(defn input->date [ymd-str db-id]
  (let [date (t.format/parse ymd-formatter ymd-str)]
    {:db/id      db-id
     :date/ymd   ymd-str
     :date/day   (t/day date)
     :date/month (t/month date)
     :date/year  (t/year date)}))

(defn input->currency [currency db-id]
  {:db/id         db-id
   :currency/code currency})

(defn input->tags [tags start-db-id]
  (let [*tag-entity (fn [db-id tag-name]
                      (hash-map :db/id db-id
                                :tag/name tag-name))
        ; Create negative ids counting down from start-db-id
        temp-ids (range start-db-id
                        (- start-db-id
                           (count tags))
                        -1)]
    (map *tag-entity temp-ids tags)))

(defn input->transaction [amount title description uuid created-at date curr tags]
  {:transaction/amount     (reader/read-string amount)
   :transaction/name       title
   :transaction/details    description
   :transaction/uuid       uuid
   :transaction/created-at created-at
   :transaction/date       (:db/id date)
   :transaction/status     :transaction.status/pending
   :transaction/currency   (:db/id curr)
   :transaction/tags       (mapv :db/id tags)})
