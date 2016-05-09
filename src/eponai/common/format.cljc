(ns eponai.common.format
  (:require [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn]]
            [clojure.set :refer [rename-keys]]
    [eponai.common.format.date :as date]
    #?(:clj
            [datomic.api :as d]
       :cljs [datascript.core :as d])))

(defn str->uuid [str-uuid]
  #?(:clj  (java.util.UUID/fromString str-uuid)
     :cljs (uuid str-uuid)))

(defn str->number [n]
  #?(:cljs (if-not (number? n)
             (cljs.reader/read-string n)
             n)
     :clj  (bigdec n)))

;; -------------------------- Database entities -----------------------------

(defn project
  "Create project db entity belonging to user with :db/id user-eid.

  Provide opts including keys that should be specifically set. Will consider keys:
  * :project/name - name of this project, default value is 'Default'.
  * :project/created-at - timestamp if when project was created, default value is now.
  * :project/uuid - UUID to assign to this project entity, default will call (d/squuid).

  Returns a map representing a project entity"
  ([user-dbid & [opts]]
   (cond->
     {:db/id              (d/tempid :db.part/user)
      :project/uuid       (or (:project/uuid opts) (d/squuid))
      :project/created-at (or (:project/created-at opts) (date/date-time->long (date/now)))
      :project/name       (or (:project/name opts) "untitled")}
     user-dbid
     (->
       (assoc :project/created-by user-dbid)
       (assoc :project/users [user-dbid])))))

(defn dashboard [project-ref & [opts]]
  {:db/id (d/tempid :db.part/user)
   :dashboard/uuid (or (:dashboard/uuid opts) (d/squuid))
   :dashboard/project project-ref})

(defn add-tempid
  "Add tempid to provided entity or collection of entities. If e is a map, assocs :db/id.
  If it's list, set or vector, maps over that collection and assoc's :db/id in each element."
  [e]
  (cond (map? e)
        (if (some? (:db/id e))
          e
          (assoc e :db/id (d/tempid :db.part/user)))

        (coll? e)
        (map (fn [v]
               (if (some? (:db/id v))
                 v
                 (assoc v :db/id (d/tempid :db.part/user)))) e)
        :else
        e))

(defn tag*
  [input]
  {:pre [(map? input)]}
  (add-tempid (select-keys input [:tag/name])))

(defn date*
  [input]
  {:post [(map? %)
          (= (count (select-keys % [:date/ymd
                                    :date/timestamp
                                    :date/year
                                    :date/month
                                    :date/day])) 5)]}
  (let [date (date/date-map input)]
    (assert (and (:date/ymd date)
                 (:date/timestamp date)) (str "Created date needs :date/timestamp or :date/ymd, got: " date))
    (add-tempid date)))

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
  * :transaction/project - takes a string UUID, returns a lookup ref.

  Returns a map representing a transaction entity"
  [input]
  (let [conv-fn-map {:transaction/currency (fn [c] {:pre [(map? c)]}
                                             (currency* c))
                     :transaction/date     (fn [d] {:pre [(map? d)]}
                                             (date* d))
                     :transaction/tags     (fn [ts] {:pre [(coll? ts)]}
                                             (map tag* ts))
                     :transaction/amount   (fn [a]
                                             {:pre [(string? a)]}
                                             #?(:clj  (bigdec a)
                                                :cljs (cljs.reader/read-string a)))
                     :transaction/project   (fn [b] {:pre [(map? b)]}
                                             (assert (:project/uuid b))
                                             [:project/uuid (:project/uuid b)])
                     :transaction/type     (fn [t] {:pre [(keyword? t)]}
                                             {:db/ident t})}
        update-fn (fn [m k]
                    (if (get m k)
                      (update m k (get conv-fn-map k))
                      m))
        transaction (reduce update-fn input (keys conv-fn-map))]

    (assoc transaction
      :db/id (d/tempid :db.part/user))))

(defn filter*
  [input]
  (debug "Doing data filter: " input)
  (let [remove-empty-vals (fn [i]
                            (into {} (filter #(let [v (val %)]
                                               (if (coll? v)
                                                 (seq v)
                                                 (some? v))))
                                  i))]
    ;(reduce
    ;  (fn [m [k v]]
    ;    (let [ent (add-tempid v)]
    ;      ;; Clear up and remove any empty filters, we don't want to insert nil in datomic.
    ;      (if (some? ent)
    ;        (assoc m k ent)
    ;        m)))
    ;  {}
    ;  f)
    (debug "Formatting filter: " (-> input
                                     (select-keys [:filter/last-x-days
                                                   :filter/start-date
                                                   :filter/end-date
                                                   :filter/min-amount
                                                   :filter/max-amount
                                                   :filter/include-tags
                                                   :filter/exclude-tags])
                                     add-tempid
                                     (update :filter/end-date add-tempid)
                                     (update :filter/start-date add-tempid)
                                     (update :filter/include-tags add-tempid)
                                     (update :filter/exclude-tags add-tempid)
                                     (update :filter/min-amount #(when % (str->number %)))
                                     (update :filter/max-amount #(when % (str->number %)))
                                     remove-empty-vals))
    (-> input
        (select-keys [:filter/last-x-days
                      :filter/start-date
                      :filter/end-date
                      :filter/min-amount
                      :filter/max-amount
                      :filter/include-tags
                      :filter/exclude-tags])
        add-tempid
        (update :filter/end-date add-tempid)
        (update :filter/start-date add-tempid)
        (update :filter/include-tags add-tempid)
        (update :filter/exclude-tags add-tempid)
        (update :filter/min-amount #(when % (str->number %)))
        (update :filter/max-amount #(when % (str->number %)))
        remove-empty-vals)))

(defn track-function* [input]
  (add-tempid (select-keys input [:track.function/id
                                  :track.function/group-by])))

(defn track* [input]
  (cond-> (add-tempid (select-keys input [:track/functions]))
          (seq (:track/functions input))
          (update :track/functions #(mapv track-function* %))))

(defn cycle* [input]
  (-> input
      (select-keys [:cycle/period
                    :cycle/period-count
                    :cycle/repeat
                    :cycle/start-date])
      add-tempid))
(defn goal* [input]
  (cond->
    (-> input
        (select-keys [:goal/cycle
                      :goal/value
                      :goal/filter])
        add-tempid
        (update :goal/value str->number)
        (update :goal/cycle cycle*))

    (some? (:goal/filter input))
    (update :goal/filter filter*)))

(defn report* [input]
  (cond
    (some? (:report/track input))
    (-> input
        (select-keys [:report/track :report/title])
        add-tempid
        (update :report/track track*))

    (some? (:report/goal input))
    (-> input
        (select-keys [:report/goal :report/title])
        add-tempid
        (update :report/goal goal*))))

(defn graph* [input]
  (cond->
    (-> input
        (select-keys [:graph/style :graph/filter])
        add-tempid)

    (some? (:graph/filter input))
    (update :graph/filter filter*)))

(defn widget* [input]
  (cond->
    (-> input
        (select-keys [:widget/filter :widget/graph :widget/report :widget/dashboard
                      :widget/index :widget/height :widget/width :widget/uuid])
        add-tempid
        (update :widget/dashboard (fn [d-uuid] (vector :dashboard/uuid d-uuid))))

    (some? (:widget/filter input))
    (update :widget/filter filter*)))

(defn widget-create [input]
  (assert (some? (:widget/dashboard input)) "Widget needs to ba associated to a dashboard.")
  (assert (some? (:widget/uuid input)) "Widget needs a UUID to be saved.")

  (-> input
      widget*
      (update :widget/graph graph*)
      (update :widget/report report*)))

(defn widget-edit [input]
  (assert (some? (:widget/dashboard input)) "Widget needs to ba associated to a dashboard.")
  (assert (some? (:widget/uuid input)) "Widget needs a UUID to be saved.")

  (-> input
      widget*
      (update :widget/graph graph*)
      (update :widget/report report*)))

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
                                                       :transaction/amount (str->number v)
                                                       :transaction/currency (add-tempid v)
                                                       :transaction/type (add-tempid v)
                                                       :transaction/date (add-tempid v)
                                                       :transaction/project {:project/uuid (str->uuid (:project/uuid v))}
                                                       v)))
                                        {})))]
    (cond-> [transaction]
            (seq tags)
            (into (mapcat tag->txs) tags))))