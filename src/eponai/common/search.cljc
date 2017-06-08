(ns eponai.common.search
  (:require
    [eponai.common.database :as db]
    [datascript.core :as datascript]
    [clojure.string :as str]))

(def search-schema
  {:search/letters    {:db/index  true
                       :db/unique :db.unique/identity}
   :search/parent     {:db/index     true
                       :db/valueType :db.type/ref}
   :search/matches    {:db/valueType   :db.type/ref
                       :db/index       true
                       :db/cardinality :db.cardinality/many}
   :search.match/refs {:db/valueType   :db.type/ref
                       :db/cardinality :db.cardinality/many
                       :db/index       true}})

(def index-depth 3)

(defn limit-depth [s]
  (if (> (count s) index-depth)
    (subs s 0 index-depth)
    s))

(defn- search-parents [word]
  (when-let [parent (seq (butlast word))]
    (let [parent (apply str parent)]
      {:search/parent (merge {:search/letters parent}
                            (search-parents parent))})))

(defn entities-by-attr-tx [attr entities]
  {:pre [(every? (every-pred (comp number? :db/id)
                             (comp string? #(get % attr)))
                 entities)]}
  (into []
        (mapcat
          (fn [e]
            (sequence
              (comp
                (map str/lower-case)
                (map (fn [word]
                       (let [letters (limit-depth word)]
                         (merge {:search/letters letters
                                 :search/matches [{:search.match/word word
                                                   :search.match/refs [{:db/id (:db/id e)}]}]}
                                (search-parents letters))))))
              (str/split (get e attr) #" "))))
        entities))

(defn matches [db search]
  (let [search (str/lower-case search)
        query {:find    '[?word (count ?refs)]
               :where   '[[?search :search/letters ?input]
                          [?search :search/matches ?match]
                          [?match :search.match/word ?word]
                          [?match :search.match/refs ?refs]]}]
    (letfn [(match-short-str [s]
              (let [entity (db/lookup-entity db [:search/letters s])
                    matches (into []
                                  (filter (fn [[word _]] (str/starts-with? word search)))
                                  (db/find-with db (db/merge-query
                                                     query
                                                     {:symbols {'?input s}})))]
                (into matches
                      (comp (map :search/letters)
                            (mapcat match-short-str))
                      (:search/_parent entity))))]
      (->> (match-short-str (limit-depth search))
           (sort-by second)))))


(comment
  (def stuff (let [lines (->> (slurp "/usr/share/dict/web2a")
                              (str/split-lines))
                   conn (datascript/create-conn search-schema)
                   _ (db/transact conn (into [] (map (fn [l] {:name l})) lines))
                   db (db/db conn)
                   index-tx (->> (db/datoms (db/db conn) :aevt :name)
                                 (into [] (comp (map :e)
                                                (map #(db/entity (db/db conn) %))))
                                 (entities-by-attr-tx :name))
                   indexed-db (datascript/db-with db index-tx)]
               {:db indexed-db :tx index-tx :lines lines})))