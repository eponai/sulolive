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
   :search.match/word {:db/unique :db.unique/identity}
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

(defn matches
  "Returns tuples of [<word> [<ref> ...]] matching the search."
  [db search]
  (let [indexed-val (limit-depth (str/lower-case search))]
    ;; Start from the largest indexed value
    (->> (db/datoms db :avet :search/letters indexed-val)
         ;; Navigate children if necessary (i.e. search "a", wanting everything starting with "a").
         (iterate (fn [datoms]
                    (mapcat #(db/datoms db :avet :search/parent (:e %)) datoms)))
         ;; This transducer almost equal to
         ;{:find  '[?word [?refs ...]]
         ; :where '[[?search :search/letters ?input]
         ;          [?search :search/matches ?match]
         ;          [?match :search.match/word ?word]
         ;          [?match :search.match/refs ?refs]]}
         (into []
               (comp
                 (take-while seq)
                 cat
                 (map :e)
                 (mapcat #(db/datoms db :eavt % :search/matches))
                 (map :v)
                 (map #(db/entity db %))
                 ;; Keep only matches that start with the search (which might be
                 ;; longer than indexed depth).
                 (filter #(str/starts-with? (:search.match/word %) search))
                 (map (juxt :search.match/word :search.match/refs)))))))

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
