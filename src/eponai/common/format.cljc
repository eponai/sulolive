(ns eponai.common.format
  #?(:clj
     (:refer-clojure :exclude [ref]))
  (:require
    [clojure.data :as diff]
    [eponai.common.database :as db :refer [tempid squuid]]
    ;; [eponai.common.database.functions :as dbfn]
    [eponai.common.format.date :as date]
    #?(:cljs
       [goog.crypt :as crypt])
    ;[datascript.core :as datascript]
    ;[datascript.db]
    [taoensso.timbre :refer [debug error info warn]])
  #?(:clj
     (:import
       (java.util UUID))))

(defn str->uuid [str-uuid]
  #?(:clj  (UUID/fromString str-uuid)
     :cljs (uuid str-uuid)))

(defn str->number [n]
  #?(:cljs (if-not (number? n)
             (cljs.reader/read-string n)
             n)
     :clj  (bigdec n)))

(defn str->bytes [s]
  (when (some? s)
    #?(:cljs (crypt/stringToUtf8ByteArray s)
       :clj  (.getBytes s "UTF-8"))))

(defn bytes->str [bytes]
  (when (some? bytes)
    #?(:cljs (crypt/utf8ByteArrayToString bytes)
       :clj  (String. bytes "UTF-8"))))

(defn remove-nil-keys [m]
  (not-empty (into {} (remove (fn [[k v]] (nil? v)) m))))

;; -------------------------- Database entities -----------------------------

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
           :project/name       (or (:project/name opts) "My Project")
           :project/categories #{(add-tempid {:category/name "Housing"})
                                 (add-tempid {:category/name "Transport"})}}
          (some? user-dbid)
          (->
            (assoc :project/created-by user-dbid)
            (assoc :project/users [user-dbid]))))


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

(defn bigdec! [x]
  #?(:clj  (bigdec x)
     :cljs (cond-> x
                   (string? x)
                   (cljs.reader/read-string))))

(defn lookup-ref? [x]
  (and (sequential? x)
       (count (= 2 x))))



(defn category [category-name]
  (category* {:category/name category-name}))

;; TODO: Uncomment?
;; Commented until we use eponai.common.database.functions again
;; for our jourmoney-generic-edit

;(defn edit-txs [{:keys [old new]} conform-fn created-at]
;  {:pre [(some? (:db/id old))
;         (= (:db/id old) (:db/id new))
;         (or (number? created-at)
;             (= ::dbfn/client-edit created-at))]}
;  (let [edits-by-attr (->> (diff/diff old new)
;                           (take 2)
;                           (mapv conform-fn)
;                           (zipmap [:old-by-attr :new-by-attr])
;                           (mapcat (fn [[id m]]
;                                     (map #(hash-map :id id :kv %) m)))
;                           (group-by (comp first :kv)))]
;    (->> edits-by-attr
;         (remove (fn [[attr]] (= :db/id attr)))
;         (mapv (fn [[attr changes]]
;                 (let [{:keys [old-by-attr new-by-attr]} (reduce (fn [m {:keys [id kv]}]
;                                                                   (assert (= (first kv) attr))
;                                                                   (assoc m id (second kv)))
;                                                                 {}
;                                                                 changes)]
;                   [:db.fn/edit-attr created-at (:db/id old) attr {:old-value old-by-attr
;                                                                   :new-value new-by-attr}]))))))

;(defn client-edit [env k params conform-fn]
;  (->> (edit-txs params conform-fn ::dbfn/client-edit)
;       (mapcat (fn [[_ created-at eid attr old-new]]
;                 (assert (number? eid) (str "entity id was not number for client edit: " [k eid attr old-new]))
;                 (binding [dbfn/cardinality-many? dbfn/cardinality-many?-datascript
;                           dbfn/ref? dbfn/ref?-datascript
;                           dbfn/unique-datom dbfn/unique-datom-datascript
;                           dbfn/tempid? dbfn/tempid?-datascript
;                           dbfn/update-edit dbfn/update-edit-datascript]
;                   (debug [:eid eid :attr attr :old-new old-new])
;                   (dbfn/edit-attr (db/db (:state env)) created-at eid attr old-new))))
;       (vec)))

;(defn server-edit [env k params conform-fn]
;  (let [created-at (some :eponai.common.parser/created-at [params env])]
;    (assert (some? created-at)
;            (str "No created-at found in either params or env for edit: " k " params: " params))
;    (edit-txs params conform-fn created-at)))

;(defn edit
;  [env k p conform-fn]
;  {:pre [(some? (:eponai.common.parser/server? env))]}
;  (if (:eponai.common.parser/server? env)
;    (server-edit env k p conform-fn)
;    (client-edit env k p conform-fn)))

(defn chat-message [db store user text]
  (when-not (db/dbid? (:db/id user))
    (throw (ex-info "No user id found in when sending chat message."
                    {:store        store
                     :text         text
                     :current-auth user})))
  (let [chat-id (or (db/one-with db {:where [['?e :chat/store (:db/id store)]]})
                    (db/tempid :db.part/user))
        message-id (db/tempid :db.part/user)]
    (-> [{:db/id      chat-id
          :chat/store (:db/id store)}
         {:db/id             message-id
          :chat.message/user (select-keys user [:db/id])
          :chat.message/text text}
         [:db/add chat-id :chat/messages message-id]]
        (with-meta {::message-id message-id
                    ::chat-id    chat-id}))))
(defn photo [url]
  {:pre [(string? url)]}
  {:db/id      (db/tempid :db.part/user)
   :photo/path url})
