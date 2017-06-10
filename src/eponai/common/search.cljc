(ns eponai.common.search
  (:require
    [eponai.common.database :as db]
    [datascript.core :as datascript]
    [clojure.string :as str]
    [clojure.set :as set]
    [taoensso.timbre :refer [debug]]))

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

(def index-depth 2)

(defn limit-depth [s]
  (if (> (count s) index-depth)
    (subs s 0 index-depth)
    s))

(defn- search-parents [word]
  (when-let [parent (seq (butlast word))]
    (let [parent (apply str parent)]
      {:search/parent (merge {:search/letters parent}
                            (search-parents parent))})))

(defn entities-by-attr-tx
  ([attr]
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
         (str/split (get e attr) #" ")))))
  ([attr entities]
   {:pre [(every? (every-pred (comp number? :db/id)
                              (comp string? #(get % attr)))
                  entities)]}
   (sequence (entities-by-attr-tx attr) entities)))

(defn match-word
  "Returns tuples of [<word> #{<ref-id> ...}] matching the search."
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
                 ;; Get the matches word
                 (map (fn [search-match]
                        (first (db/datoms db :eavt search-match :search.match/word))))
                 ;; Keep only matches that start with the search. Searches might be
                 ;; longer than indexed depth, so we need to do this check.
                 (filter (fn [search-match-datom]
                           (str/starts-with? (:v search-match-datom) search)))
                 (map (juxt :v #(into #{}
                                      (map :v)
                                      (db/datoms db :eavt (:e %) :search.match/refs)))))))))

(defn partial-match-ref [db needle search-id]
  (let [search-matches (when (some? search-id)
                         (into #{}
                               (map :v)
                               (db/datoms db :eavt search-id :search/matches)))
        xf (comp
             ;; search.match
             (map :e)
             (filter (if (seq search-matches)
                       #(contains? search-matches %)
                       identity))
             (map #(:v (first (db/datoms db :eavt % :search.match/word))))
             (filter (fn [word]
                       (str/starts-with? word needle))))]
    (fn [ref]
      (transduce
        xf
        (completing (fn [_ word]
                      (reduced word)))
        nil
        (db/datoms db :avet :search.match/refs ref)))))

(defn match-string [db s]
  (let [sanitized-needles (db/sanitized-needles s)
        needle-order (into {} (map-indexed #(vector %2 %1)) sanitized-needles)
        [needle & needles] (sort-by count #(compare %2 %1) sanitized-needles)]
    (reduce
      (fn [match needle]
        (if (empty? match)
          (reduced nil)
          (let [index-needle (limit-depth (str/lower-case needle))
                needle-search-id (when (== index-depth (count index-needle))
                                   (:e (first (db/datoms db :avet :search/letters index-needle))))
                match-ref-fn (partial-match-ref db needle needle-search-id)]
            (mapcat (fn [[word refs]]
                      (transduce
                        (comp (map (fn [ref]
                                     (when-let [w (match-ref-fn ref)]
                                       [(str word " " w) ref])))
                              (filter some?))
                        (completing (fn [m [k ref]]
                                      (assoc! m k (conj (get m k #{}) ref)))
                                    persistent!)
                        (transient {})
                        refs))
                    match))))
      (match-word db needle)
      needles)))

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
               {:db indexed-db :tx index-tx :lines lines}))

  (run!
    (fn [search]
      (prn "Running search: " search)
      (time (debug "count: " (count (match-string (:db stuff) search)))))
    ["acid"
     "acid bath"
     "ac bath"
     "a bat"
     "a ba"
     "a b"
     "a b c"
     "a b c d"])
  )
