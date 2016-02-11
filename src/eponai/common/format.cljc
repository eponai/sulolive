(ns eponai.common.format
  (:require [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn]]
            [clojure.set :refer [rename-keys]]
    #?@(:clj  [[clj-time.core :as t]
               [clj-time.format :as f]
               [clj-time.coerce :as c]]
        :cljs [[cljs-time.core :as t]
               [cljs-time.format :as f]
               [cljs-time.coerce :as c]
               [goog.date.DateTime]])
    #?(:clj  [datomic.api :as d]
       :cljs [datascript.core :as d])))

;; Warning: clj(s)-time libraries are sometimes inconsistent.
;; Example:
;; clj:  (c/to-long (c/to-date-time "2015-10-10"))       => 1444435200000
;; cljs: (c/to-long (c/to-date-time "2015-10-10"))       => 1444435200000
;; clj:  (c/to-long (c/to-local-date-time "2015-10-10")) => 1444435200000
;; cljs: (c/to-long (c/to-local-date-time "2015-10-10")) => 1444406400000 !!
;; clj:  (c/to-long (fo/parse-local "2015-10-10")) => 1444435200000
;; cljs: (c/to-long (fo/parse-local "2015-10-10")) => 1444406400000 !!

(defn ensure-date
  "Takes any date (js/Date, a string, goog.date.DateTime, etc..), and
   turns it into a date instance that works with clj(s)-time."
  [date]
  #?(:clj  date
     :cljs (cond
             (instance? goog.date.DateTime date) date
             (instance? js/Date date) (doto (goog.date.DateTime.)
                                        (.setTime (.getTime date)))
             :else (do
                     (warn "Using unknown date instance: " date)
                     date))))

#?(:cljs
   (defn js-date->utc-ymd-date
     "Takes a js/Date and returns a UTC date with only the year, month and day
     components.

     I recommend calling this function for all dates created with (js/Date.),
     since local dates seems bugged in cljs-time."
     [date]
     {:pre  [(or (nil? date) (instance? js/Date date))]
      :post [(instance? js/Date %)]}
     (some->> date
              (ensure-date)
              (f/unparse-local (f/formatters :date))
              (c/to-date))))

#?(:cljs
   (defn random-string->js-date [s]
     (some-> s
             f/parse
             c/to-date)))

(defn date->timestamp
  "Returns timestamp of a date"
  [date]
  (c/to-long (ensure-date date)))

(defn ymd-string->date [ymd]
  {:pre [(string? ymd)]}
  (f/parse ymd))

(defn date->ymd-string
  "Takes a date and returns a string for that date of the form yyyy-MM-dd."
  [date]
  (when date
    (f/unparse (f/formatters :date) (ensure-date date))))

(defn budget->db-tx [user-eid input-uuid input-name]
  {:db/id             (d/tempid :db.part/user)
   :budget/name       input-name
   :budget/uuid       input-uuid
   :budget/created-by user-eid
   :budget/created-at (date->timestamp (t/now))})

(defn date-str->db-tx
  "Returns a map representing a date datomic entity for the specified date in
  string format yyyy-MM-dd."
  [date-str]
  {:pre [(string? date-str)]}
  (let [date (ymd-string->date date-str)]
    {:db/id          (d/tempid :db.part/user)
     :date/ymd       date-str
     :date/year      (t/year date)
     :date/month     (t/month date)
     :date/day       (t/day date)
     :date/timestamp (date->timestamp date)}))

(defn tags->db-tx
  "Returns datomic entities representing tags, given a vector of tag names."
  [tags]
  (map (fn [n] {:db/id          (d/tempid :db.part/user)
                :tag/name       n}) tags))

(defn str->uuid [str-uuid]
  #?(:clj  (java.util.UUID/fromString str-uuid)
     :cljs (uuid str-uuid)))

(defn user-transaction->db-entity
  "Takes a user input transaction and converts into a datomic entity.
  A conversion function will be applied on the values for the following
  keys #{:currency :date :tags :amount :uuid} as appropriate for the database.
  All keys will be prefixed with 'transaction/' (e.g. :name -> :transaction/name)"
  [user-tx]
  (let [conv-fn-map {:transaction/currency (fn [c] [:currency/code c])
                     :transaction/date     (fn [d] (date-str->db-tx d))
                     :transaction/tags     (fn [t] (tags->db-tx t))
                     :transaction/amount   (fn [a] #?(:clj  (bigint a)
                                                      :cljs (cljs.reader/read-string a)))
                     :transaction/budget   (fn [b] [:budget/uuid (str->uuid (str b))])}
        update-fn (fn [m k] (update m k (conv-fn-map k)))]
    (assoc (reduce update-fn user-tx (keys conv-fn-map))
      :db/id (d/tempid :db.part/user))))

(defn input-transaction->db-entity
  "Takes a user input transaction with namespace :input and converts into a datomic entity.
  (e.g. :input/title -> :transaction/title etc)
  Will change the namespace to :transaction and update values for the following keys:
   #{:input/currency :input/date :input/tags :input/amount :input/budget} as appropriate for the database."
  [input-transaction]
  (let [user-tx (reduce (fn [m [k v]]
                         (assoc m (keyword "transaction" (name k)) v))
                       {}
                       input-transaction)
        conv-fn-map {:transaction/currency (fn [c] [:currency/code c])
                     :transaction/date     (fn [d] (date-str->db-tx d))
                     :transaction/tags     (fn [t] (tags->db-tx t))
                     :transaction/amount   (fn [a] #?(:clj  (bigint a)
                                                      :cljs (cljs.reader/read-string a)))
                     :transaction/budget   (fn [b] [:budget/uuid (str->uuid (str b))])}
        update-fn (fn [m k] (update m k (conv-fn-map k)))]
    (assoc (reduce update-fn user-tx (keys conv-fn-map))
      :db/id (d/tempid :db.part/user))))
