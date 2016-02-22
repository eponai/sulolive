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

(defn str->uuid [str-uuid]
  #?(:clj  (java.util.UUID/fromString str-uuid)
     :cljs (uuid str-uuid)))

;; -------------------------- Database entities -----------------------------

(defn budget
  "Create budget db entity belonging to user with :db/id user-eid.

  Provide opts including keys that should be specifically set. Will consider keys:
  * :budget/name - name of this budget, default value is 'Default'.
  * :budget/created-at - timestamp if when budget was created, default value is now.
  * :budget/uuid - UUID to assign to this budget entity, default will call (d/squuid).

  Returns a map representing a budget entity"
  ([user-dbid & [opts]]
   (cond->
     {:db/id             (d/tempid :db.part/user)
      :budget/uuid       (or (:budget/uuid opts) (d/squuid))
      :budget/created-at (or (:budget/created-at opts) (c/to-long (t/now)))
      :budget/name       (or (:budget/name opts) "Default")}
     user-dbid
     (assoc :budget/created-by user-dbid))))

(defn dashboard [budget-ref & [opts]]
  {:db/id (d/tempid :db.part/user)
   :dashboard/uuid (or (:dashboard/uuid opts) (d/squuid))
   :dashboard/budget budget-ref})

(defn date
  "Create a date entity.

  Takes a \"yyyy-MM-dd\" string, returns a map representing a date entity."
  [date-str]
  {:pre [(string? date-str)]}
  (let [date (ymd-string->date date-str)]
    {:db/id          (d/tempid :db.part/user)
     :date/ymd       date-str
     :date/year      (t/year date)
     :date/month     (t/month date)
     :date/day       (t/day date)
     :date/timestamp (date->timestamp date)}))

(defn tags
  "Create a collection of tag entities, given a colleciton of strings."
  [tags]
  (map (fn [n] {:db/id          (d/tempid :db.part/user)
                :tag/name       n}) tags))

(defn transaction
  "Create a transaction entity for the given input. Will replace the name space of the keys to the :transaction/ namespace

  Provide opts for special behavior, will consider keys:
  * :no-rename - set this key to not rename the namespace of the keys.

  Calls special functions on following keys to format according to datomic:
  * :transaction/currency - takes a currency code string, returns a currency entity.
  * :transaction/date - takes a \"yyy-MM-dd\" string, returns a date entity.
  * :transaction/tags - takes a collections of strings, returns a collection of tag entities.
  * :transaction/amount - takes a number string, returns a number.
  * :transaction/budget - takes a string UUID, returns a lookup ref.

  Returns a map representing a transaction entity"
  [input & [opts]]
  (let [user-tx (if (:no-rename opts)
                  input
                  (reduce (fn [m [k v]]
                            (assoc m (keyword "transaction" (name k)) v))
                          {}
                          input))
        conv-fn-map {:transaction/currency (fn [c] [:currency/code c])
                     :transaction/date     (fn [d] (date d))
                     :transaction/tags     (fn [ts] (tags ts))
                     :transaction/amount   (fn [a] #?(:clj  (bigint a)
                                                      :cljs (cljs.reader/read-string a)))
                     :transaction/budget   (fn [b] [:budget/uuid (str->uuid (str b))])}
        update-fn (fn [m k] (update m k (conv-fn-map k)))
        transaction (reduce update-fn user-tx (keys conv-fn-map))]

    (assoc transaction
      :db/id (d/tempid :db.part/user))))

(defn widget-map [{:keys [input-function input-report input-graph input-widget] :as input}]
  (let [function (assoc input-function :db/id (d/tempid :db.part/user))

        report (assoc input-report :db/id (d/tempid :db.part/user)
                                   :report/functions #{(:db/id function)})

        graph (assoc input-graph :db/id (d/tempid :db.part/user))

        widget (merge input-widget
                      {:db/id         (d/tempid :db.part/user)
                       :widget/graph  (:db/id graph)
                       :widget/report (:db/id report)})
        dashboard-uuid (:dashboard/uuid (:input-dashboard input))]
    {:function function
     :report   report
     :graph    graph
     :widget   widget
     :add      [:db/add [:dashboard/uuid dashboard-uuid] :dashboard/widgets (:db/id widget)]}))
