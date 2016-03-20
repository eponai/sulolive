(ns eponai.common.format
  (:require [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn]]
            [clojure.set :refer [rename-keys]]
    [eponai.common.validate :as validate]
    #?@(:clj  [
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]]
        :cljs [[cljs-time.core :as t]
               [cljs-time.format :as f]
               [cljs-time.coerce :as c]
               [goog.date.DateTime]])
    #?(:clj
            [datomic.api :as d]
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

(defn ymd-string->js-date [ymd]
  {:pre [(string? ymd)]}
  (c/to-date (f/parse ymd)))

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

(defn add-tempid
  "Add tempid to provided entity or collection of entities. If e is a map, assocs :db/id.
  If it's list, set or vector, maps over that collection and assoc's :db/id in each element."
  [e]
  {:pre [(coll? e)]}
  (cond (map? e)
        (assoc e :db/id (d/tempid :db.part/user))

        (coll? e)
        (map #(assoc % :db/id (d/tempid :db.part/user)) e)))

(defn str->date
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

(defn tag*
  [input]
  {:pre [(map? input)]}
  (add-tempid (select-keys input [:tag/name])))

(defn date*
  [input]
  {:pre [(map? input)]}
  (let [d (select-keys input [:date/ymd])
        date (ymd-string->date (:date/ymd d))]
    (merge d
           {:db/id          (d/tempid :db.part/user)
            :date/year      (t/year date)
            :date/month     (t/month date)
            :date/day       (t/day date)
            :date/timestamp (date->timestamp date)})))

(defn currency*
  [input]
  {:pre [(map? input)]}
  (add-tempid (select-keys input [:currency/code])))

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
  [input]
  (let [input-transaction (validate/transaction-keys input)
        conv-fn-map {:transaction/currency (fn [c] {:pre [(map? c)]}
                                             (currency* c))
                     :transaction/date     (fn [d] {:pre [(map? d)]}
                                             (date* d))
                     :transaction/tags     (fn [ts] {:pre [(coll? ts)]}
                                             (map tag* ts))
                     :transaction/amount   (fn [a]
                                             #?(:clj  (bigint a)
                                                :cljs (cljs.reader/read-string a)))
                     :transaction/budget   (fn [b] {:pre [(map? b)]}
                                             (assert (:budget/uuid b))
                                             [:budget/uuid (:budget/uuid b)])
                     :transaction/type     (fn [t] {:pre [(keyword? t)]}
                                             {:db/ident t})}
        update-fn (fn [m k]
                    (if (get m k)
                      (update m k (get conv-fn-map k))
                      m))
        transaction (reduce update-fn input-transaction (keys conv-fn-map))]

    (assoc transaction
      :db/id (d/tempid :db.part/user))))

(defn data-filter
  [input & opts]
  (let [filter-value-format (fn [k v]
                              (cond (or (= k :filter/include-tags)
                                        (= k :filter/exclude-tags))
                                    ;; These are tags, assoc db/id
                                    (map #(assoc % :db/id (d/tempid :db.part/user)) v)

                                    (or (= k :filter/start-date)
                                        (= k :filter/end-date))
                                    ;; These are dates, create a date entity
                                    (str->date (date->ymd-string v))))
        clean-filters (reduce
                        (fn [m [k v]]
                          (let [ent (filter-value-format k v)]
                            ;; Clear up and remove any empty filters, we don't want to insert nil in datomic.
                            (if (seq ent)
                              (assoc m k ent)
                              m)))
                        {}
                        input)]
    (if (seq clean-filters)
      (assoc clean-filters :db/id (d/tempid :db.part/user))
      nil)))

(defn widget-create [{:keys [input-function input-report input-graph input-widget input-filter] :as input}]
  (let [function (add-tempid input-function)                ;(assoc input-function :db/id (d/tempid :db.part/user))
        report (assoc (add-tempid input-report) :report/functions #{(:db/id function)})
        graph (add-tempid input-graph)
        filter (data-filter input-filter)
        widget (cond-> (merge input-widget
                              {:db/id            (d/tempid :db.part/user)
                               :widget/graph     (:db/id graph)
                               :widget/report    (:db/id report)
                               :widget/dashboard [:dashboard/uuid (:dashboard/uuid (:input-dashboard input))]})
                       filter
                       (assoc :widget/filter (:db/id filter)))]
    (cond-> {:function function
             :report   report
             :graph    graph
             :widget   widget}
            filter
            (assoc :filter filter))))


(defn transaction-create [k {:keys [transaction/tags] :as params}]
  (if-not (= (frequencies (set tags))
             (frequencies tags))
    (throw (ex-info "Illegal argument :input-tags. Each tag must be unique."
                    {:input-tags tags
                     :mutate     k
                     :params     params}))
    (transaction params)))

(defn transaction-edit [{:keys [transaction/tags
                                transaction/uuid] :as input-transaction}]
  (let [tag->txs (fn [{:keys [tag/removed tag/name] :as tag}]
                   {:pre [(some? name)]}
                   (if removed
                     [[:db/retract [:transaction/uuid uuid] :transaction/tags [:tag/name name]]]
                     (let [tempid (d/tempid :db.part/user)]
                       ;; Create new tag and add it to the transaction
                       [(-> tag (dissoc :tag/removed) (assoc :db/id tempid))
                        [:db/add [:transaction/uuid uuid] :transaction/tags tempid]])))
        transaction (-> input-transaction
                        (dissoc :transaction/tags)
                        (assoc :db/id (d/tempid :db.part/user))
                        (->> (reduce-kv (fn [m k v]
                                          (assoc m k (condp = k
                                                       :transaction/amount #?(:cljs (cljs.reader/read-string v) :clj (bigint v))
                                                       :transaction/currency (assoc v :db/id (d/tempid :db.part/user))
                                                       :transaction/type (assoc v :db/id (d/tempid :db.part/user))
                                                       :transaction/date (str->date (:date/ymd v))
                                                       :transaction/budget {:budget/uuid (str->uuid (:budget/uuid v))}
                                                       v)))
                                        {})))]
    (cond-> [transaction]
            (seq tags)
            (into (mapcat tag->txs) tags))))