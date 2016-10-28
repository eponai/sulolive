(ns eponai.common.format
  #?(:clj (:refer-clojure :exclude [ref]))
  (:require [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn]]
            [clojure.set :refer [rename-keys]]
            [clojure.data :as diff]
            [eponai.common.database.functions :as dbfn]
            [eponai.common.format.date :as date]
            #?(:clj [datomic.api :as datomic])
            [datascript.core :as datascript]
            [datascript.db])
  #?(:clj (:import [datomic Connection]
                   [clojure.lang Atom])))

(defn tempid [partition & [n]]
  #?(:clj (apply datomic/tempid (cond-> [partition] (some? n) (conj n)))
     :cljs (apply datascript/tempid (cond-> [partition] (some? n) (conj n)))))

#?(:clj
   (def tempid-type (type (tempid :db.part/user))))

(defn tempid? [x]
  #?(:clj (= tempid-type (type x))
     :cljs (and (number? x) (neg? x))))

(defn dbid? [x]
  #?(:clj  (or (tempid? x) (number? x))
     :cljs (number? x)))

(defn squuid []
  ;; Works for both datomic and datascript.
  (datascript/squuid))

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
  [user-dbid & [opts]]
  (cond-> {:db/id              (tempid :db.part/user)
           :project/uuid       (or (:project/uuid opts) (squuid))
           :project/created-at (or (:project/created-at opts) (date/date-time->long (date/now)))
           :project/name       (or (:project/name opts) "My Project")}
          (some? user-dbid)
          (->
            (assoc :project/created-by user-dbid)
            (assoc :project/users [user-dbid]))))


(defn dashboard [project-ref & [opts]]
  {:db/id             (tempid :db.part/user)
   :dashboard/uuid    (or (:dashboard/uuid opts) (squuid))
   :dashboard/project project-ref})

(defn add-tempid
  "Add tempid to provided entity or collection of entities. If e is a map, assocs :db/id.
  If it's list, set or vector, maps over that collection and assoc's :db/id in each element."
  [e]
  (cond (map? e)
        (if (some? (:db/id e))
          e
          (assoc e :db/id (tempid :db.part/user)))

        (coll? e)
        (map (fn [v]
               (if (some? (:db/id v))
                 v
                 (assoc v :db/id (tempid :db.part/user))))
             e)
        :else
        e))

(defn category*
  [input]
  {:pre [(map? input)]}
  (add-tempid (select-keys input [:db/id :category/name])))

(defn tag*
  [input]
  {:pre [(map? input)]}
  (add-tempid (select-keys input [:db/id :tag/name])))

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
  (add-tempid (select-keys input [:db/id :currency/code])))

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
  (let [conv-fn-map {:transaction/date     (fn [d] {:pre [(map? d)]}
                                             (date* d))
                     :transaction/tags     (fn [ts]
                                             {:pre [(coll? ts)]}
                                             (into #{} (comp (filter some?) (map tag*)) ts))
                     :transaction/category (fn [c]
                                             {:pre [(map? c)]}
                                             c)
                     :transaction/amount   (fn [a]
                                             {:pre [(or (string? a) (number? a))]}
                                             #?(:clj  (bigdec a)
                                                :cljs (cond-> a
                                                              (string? a)
                                                              (cljs.reader/read-string))))
                     :transaction/type     (fn [t] {:pre [(or (keyword? (:db/ident t t)))]}
                                             {:db/ident t})}
        update-fn (fn [m k]
                    (if (get m k)
                      (update m k (get conv-fn-map k))
                      m))
        transaction (reduce update-fn input (keys conv-fn-map))]
    (add-tempid transaction)))

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
    ;(debug "Formatting filter: " (-> input
    ;                                 (select-keys [:db/id :filter/last-x-days
    ;                                               :filter/start-date
    ;                                               :filter/end-date
    ;                                               :filter/min-amount
    ;                                               :filter/max-amount
    ;                                               :filter/include-tags
    ;                                               :filter/exclude-tags])
    ;                                 add-tempid
    ;                                 (update :filter/end-date add-tempid)
    ;                                 (update :filter/start-date add-tempid)
    ;                                 (update :filter/include-tags add-tempid)
    ;                                 (update :filter/exclude-tags add-tempid)
    ;                                 (update :filter/min-amount #(when % (str->number %)))
    ;                                 (update :filter/max-amount #(when % (str->number %)))
    ;                                 remove-empty-vals))
    (-> input
        (select-keys [:db/id
                      :filter/last-x-days
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
  (add-tempid (select-keys input [:db/id :track.function/id
                                  :track.function/group-by])))

(defn track* [input]
  (cond-> (add-tempid (select-keys input [:db/id :track/functions]))
          (seq (:track/functions input))
          (update :track/functions #(mapv track-function* %))))

(defn cycle* [input]
  (-> input
      (select-keys [:db/id
                    :cycle/period
                    :cycle/period-count
                    :cycle/repeat
                    :cycle/start-date])
      add-tempid))
(defn goal* [input]
  (cond->
    (-> input
        (select-keys [:db/id :goal/cycle
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
        (select-keys [:db/id :report/track :report/title])
        add-tempid
        (update :report/track track*))

    (some? (:report/goal input))
    (-> input
        (select-keys [:db/id :report/goal :report/title])
        add-tempid
        (update :report/goal goal*))))

(defn graph* [input]
  (cond->
    (-> input
        (select-keys [:db/id :graph/style :graph/filter])
        add-tempid)

    (some? (:graph/filter input))
    (update :graph/filter filter*)))

(defn widget* [input]
  (cond->
    (-> input
        (select-keys [:db/id :widget/filter :widget/graph :widget/report :widget/dashboard
                      :widget/index :widget/height :widget/width :widget/uuid])
        add-tempid)

    ;(some? (:widget/dashboard input))
    ;(update :widget/dashboard (fn [d-uuid] (vector :dashboard/uuid d-uuid)))

    (some? (:widget/filter input))
    (update :widget/filter filter*)))

(defn widget-create [input]
  (assert (some? (:widget/dashboard input)) "Widget needs to ba associated to a dashboard.")
  (assert (some? (:widget/uuid input)) "Widget needs a UUID to be saved.")

  (-> input
      widget*
      (update :widget/graph graph*)
      (update :widget/report report*)))

(defn tag-filter-transactions [filters]
  {:post [(or (debug "tag-filter-transactions ret: " %) (vector? %))]}
  (debug "tag-filter-transactions input: " filters)
  (letfn [(tags->txs [{:keys [tag/status tag/name] :as tag} attr]
                    (cond (= status :deleted) [[:db/retract (:db/id filters) attr [:tag/name name]]]
                          (= status :added) (let [new-tag (tag* tag)]
                                              [(tag* tag)
                                               [:db/add (:db/id filters) attr (:db/id new-tag)]])
                          :else nil))]
    (cond-> []
            (seq (:filter/exclude-tags filters))
            (into (comp (mapcat #(tags->txs % :filter/exclude-tags))
                        (filter some?)) (:filter/exclude-tags filters))

            (seq (:filter/include-tags filters))
            (into (comp (mapcat #(tags->txs % :filter/include-tags))
                        (filter some?)) (:filter/include-tags filters)))))

(defn widget-edit [input]
  ;(assert (some? (:widget/dashboard input)) "Widget needs to ba associated to a dashboard.")
  (assert (some? (:widget/uuid input)) "Widget needs a UUID to be saved.")
  (let [widget (cond-> (widget* input)

                       (some? (:widget/graph input))
                       (update :widget/graph graph*)

                       (some? (:widget/report input))
                       (update :widget/report report*))

        widget-tag-filter-txs (tag-filter-transactions (get-in widget [:widget/filter]))
        graph-tag-filter-txs (tag-filter-transactions (get-in widget [:widget/graph :graph/filter]))
        widget (cond-> widget
                       (contains? widget :widget/filter)
                       (update-in [:widget/filter] dissoc :filter/include-tags :filter/exclude-tags)

                       (some? (get-in widget [:widget/graph :graph/filter]))
                       (update-in [:widget/graph :graph/filter] dissoc :filter/include-tags :filter/exclude-tags))

        db-id-only? (fn [m] (= #{:db/id} (set (keys m))))
        widget (cond-> widget
                       (db-id-only? (get-in widget [:widget/filter]))
                       (dissoc :widget/filter)

                       (db-id-only? (get-in widget [:widget/graph :graph/filter]))
                       (update :widget/graph dissoc :graph/filter))]
    (-> [widget]
        (into widget-tag-filter-txs)
        (into graph-tag-filter-txs))))

(defn transaction-edit [{:keys [transaction/tags transaction/uuid] :as input-transaction}]
  (let [tag->txs (fn [{:keys [tag/status tag/name] :as tag}]
                   {:pre [(some? name)]}
                   (condp = status
                     :deleted
                     [[:db/retract [:transaction/uuid uuid] :transaction/tags [:tag/name name]]]

                     nil
                     (let [tempid (tempid :db.part/user)]
                       ;; Create new tag and add it to the transaction
                       [(assoc (tag* tag) :db/id tempid)
                        [:db/add [:transaction/uuid uuid] :transaction/tags tempid]])))
        transaction (-> input-transaction
                        (dissoc :transaction/tags)
                        (assoc :db/id (tempid :db.part/user))
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

(defn edit-txs [{:keys [old new]} conform-fn created-at]
  {:pre [(some? (:db/id old))
         (= (:db/id old) (:db/id new))
         (or (number? created-at)
             (= ::dbfn/client-edit created-at))]}
  (let [diff (mapv conform-fn (diff/diff old new))
        edits-by-attr (->> (take 2 diff)
                           (zipmap [:old-by-attr :new-by-attr])
                           (mapcat (fn [[id m]]
                                     (map #(hash-map :id id :kv %) m)))
                           (group-by (comp first :kv)))]
    (->> edits-by-attr
         (remove (fn [[attr]] (= :db/id attr)))
         (mapv (fn [[attr changes]]
                 (let [{:keys [old-by-attr new-by-attr]} (reduce (fn [m {:keys [id kv]}]
                                                                   (assert (= (first kv) attr))
                                                                   (assoc m id (second kv)))
                                                                 {}
                                                                 changes)]
                   [:db.fn/edit-attr created-at (:db/id old) attr {:old-value old-by-attr
                                                                   :new-value new-by-attr}]))))))

(defn client-edit [env k params conform-fn]
  (->> (edit-txs params conform-fn ::dbfn/client-edit)
       (mapcat (fn [[_ created-at eid attr old-new]]
                 (assert (number? eid) (str "entity id was not number for client edit: " [k eid attr old-new]))
                 (binding [dbfn/cardinality-many? dbfn/cardinality-many?-datascript
                           dbfn/ref? dbfn/ref?-datascript
                           dbfn/unique-datom dbfn/unique-datom-datascript
                           dbfn/tempid? dbfn/tempid?-datascript
                           dbfn/update-edit dbfn/update-edit-datascript]
                   (debug [:eid eid :attr attr :old-new old-new])
                   (dbfn/edit-attr (datascript/db (:state env)) created-at eid attr old-new))))
       (vec)))

(defn server-edit [env k params conform-fn]
  (let [created-at (some :eponai.common.parser/created-at [params env])]
    (assert (some? created-at)
            (str "No created-at found in either params or env for edit: " k " params: " params))
    (edit-txs params conform-fn created-at)))

(defn edit
  [env k p conform-fn]
  {:pre [(some? (:eponai.common.parser/server? env))]}
  (if (:eponai.common.parser/server? env)
    (server-edit env k p conform-fn)
    (client-edit env k p conform-fn)))
