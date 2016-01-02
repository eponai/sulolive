(ns eponai.common.format
  (:require
    #?@(:clj  [[clj-time.core :as t]
               [clj-time.format :as f]
               [clj-time.coerce :as c]]
        :cljs [[cljs-time.core :as t]
               [cljs-time.format :as f]
               [cljs-time.coerce :as c]
               [goog.date.DateTime]])
    #?(:clj  [datomic.api :as d]
       :cljs [datascript.core :as d])))

(defn date->ymd-string
  "Takes a date and returns a string for that date of the form yyyy-MM-dd."
  [date]
  (f/unparse-local (f/formatters :date)
                   #?(:clj date
                      :cljs (cond
                              (instance? goog.date.DateTime date) date
                              (instance? js/Date date) (doto (goog.date.DateTime.)
                                                         (.setTime (.getTime date)))
                              :else (do
                                      (prn "Warning: Using unknown date instance: " date)
                                      date)))))

(defn date->db-tx
  "Returns a map representing a date datomic entity for the specified LocalDateTime,
  given a datomic id function (e.g. d/tempid) and a partition."
  [date]
  {:db/id          (d/tempid :db.part/user)
   :date/ymd       (date->ymd-string date)
   :date/year      (t/year date)
   :date/month     (t/month date)
   :date/day       (t/day date)
   :date/timestamp (c/to-long date)})

(defn date-str->db-tx
  "Returns a LocalDateTime for the string of format 'yyy-MM-dd',
  given a datomic id function and partition (e.g. datomic.api/tempid
  part :db.part/user)."
  [date-str]
  (date->db-tx (f/parse-local (f/formatters :date) date-str)))

(defn tags->db-tx
  "Returns datomic entities representing tags, given a vector of tag names."
  [tags]
  (map (fn [n] {:db/id          (d/tempid :db.part/user)
                :tag/name       n
                :tag/persistent false}) tags))

(defn user-transaction->db-entity
  "Takes a user input transaction and converts into a datomic entity.
  A conversion function will be applied on the values for the following
  keys #{:currency :date :tags :amount :uuid} as appropriate for the database.
  All keys will be prefixed with 'transaction/' (e.g. :name -> :transaction/name)"
  [user-tx]
  (let [conv-fn-map {:transaction/currency (fn [c] [:currency/code c])
                     :transaction/date     (fn [d] (date-str->db-tx d))
                     :transaction/tags     (fn [t] (tags->db-tx t))
                     :transaction/amount   (fn [a] #?(:clj  (bigint a) :cljs a))
                     #?@(:clj [:transaction/budget (fn [b] [:budget/uuid b])])}
        update-fn (fn [m k] (update m k (conv-fn-map k)))]
    (assoc (reduce update-fn user-tx (keys conv-fn-map))
      :db/id (d/tempid :db.part/user))))
