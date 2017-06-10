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

;; ##############
;; ## Indexing

(defn entities-by-attr-tx
  ([attr]
   (mapcat
     (fn [e]
       (if (false? (:added e))
         ;; Remove all refs to e. We need to do this given what's already in the
         ;; database.
         [[::retract-ref (:db/id e)]]
         ;; Add a ref to e and
         (sequence
           (comp
             (map str/lower-case)
             (map (fn [word]
                    (let [letters (limit-depth word)]
                      (merge {:search/letters letters
                              :search/matches [{:search.match/word word
                                                :search.match/refs [{:db/id (:db/id e)}]}]}
                             (search-parents letters))))))
           (str/split (get e attr) #" "))))))
  ([attr entities]
   {:pre [(every? (every-pred (comp number? :db/id)
                              (comp string? #(get % attr)))
                  entities)]}
   (sequence (entities-by-attr-tx attr) entities)))


(defn retract-ref-tx [db ref]
  (let [refs (db/datoms db :avet :search.match/refs ref)]
    (map (fn [[e]]
           [:db/retract e :search.match/refs ref])
         refs)))

(defn expand-transactions [db txs]
  (mapcat (fn [tx]
            (if (and (vector? tx) (= ::retract-ref (first tx)))
              (retract-ref-tx db (second tx))
              [tx]))
          txs))

(defn transact
  "Helper function for transacting search txs."
  [x txs]
  {:pre [(or (db/connection? x) (db/database? x))]}
  (let [db (cond-> x (db/connection? x) (db/db))
        expanded (expand-transactions db txs)]
    (if (db/connection? x)
      (db/transact x expanded)
      (datascript/db-with x expanded))))

(defn gc
  "Takes a db and retracts search.match and search entities that have
  no more refs."
  [db]
  (let [matches-missing-refs
        ;; Finds any search.match that has no refs
        (sequence
          (comp
            (map :e)
            (filter (fn [match]
                      (empty?
                        (db/datoms db :eavt match :search.match/refs)))))
          (db/datoms db :aevt :search.match/word))
        empty-matches-by-search
        ;; Groups matches by their search, if we need to retract searches later.
        (->> (db/find-with db {:find    '[?search ?match]
                               :where   '[[?search :search/matches ?match]]
                               :symbols {'[?match ...] matches-missing-refs}})
             (reduce (fn [m [search match]]
                       (assoc! m search ((fnil conj []) (get m search) match)))
                     (transient {}))
             (persistent!))
        ;; Retracts the search.matches
        db-empty-matches-gced
        (datascript/db-with
          db
          (sequence
            (comp cat
                  (distinct)
                  (map (fn [match] [:db.fn/retractEntity match])))
            (vals empty-matches-by-search)))
        retract-searches
        ;; Retracts search entities if they're not pointing to anything
        ;; and no one is pointing to it.
        (sequence (comp
                    (filter (fn [search]
                              (and (empty?
                                     (db/datoms db-empty-matches-gced :eavt search :search/matches))
                                   (empty?
                                     (db/datoms db-empty-matches-gced :avet :search/parent search)))))
                    (map (fn [search]
                           [:db.fn/retractEntity search])))
                  (keys empty-matches-by-search))]
    (datascript/db-with db-empty-matches-gced retract-searches)))

;; ##############
;; ## Searching

(defn match-word
  "Returns tuples of [(word-fn <word>) #{<ref-id> ...}] matching the search."
  [db search word-fn]
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
                 (map (juxt (comp word-fn :v)
                            #(into #{}
                                   (map :v)
                                   (db/datoms db :eavt (:e %) :search.match/refs)))))))))

(defn partial-match-ref [db needle search-id]
  (let [search-matches (when (some? search-id)
                         (into #{}
                               (map :v)
                               (db/datoms db :eavt search-id :search/matches)))
        xf (cond-> (map :e)
                   ;; search.match
                   (seq search-matches)
                   (comp (filter #(contains? search-matches %)))
                   :always
                   (comp (map #(:v (first (db/datoms db :eavt % :search.match/word)))))
                   (some? needle)
                   (comp (filter (fn [word]
                                   (str/starts-with? word needle)))))]
    (fn [ref]
      (into [] xf (db/datoms db :avet :search.match/refs ref)))))

(defn match-string [db s]
  (let [sanitized-needles (db/sanitized-needles s)
        needle-order (into {} (map-indexed #(vector %2 %1)) sanitized-needles)
        [needle & needles] (sort-by count #(compare %2 %1) sanitized-needles)]
    (->> needles
         (reduce
           (fn [match needle]
             (if (empty? match)
               (reduced nil)
               (let [index-needle (limit-depth (str/lower-case needle))
                     needle-search-id (when (== index-depth (count index-needle))
                                        (:e (first (db/datoms db :avet :search/letters index-needle))))
                     match-ref-fn (partial-match-ref db needle needle-search-id)
                     word-position (get needle-order needle)]
                 (mapcat (fn [[words refs]]
                           (transduce
                             (comp (mapcat (fn [ref]
                                             (map (fn [w]
                                                    [(assoc words word-position w) ref])
                                                  (match-ref-fn ref)))))
                             (completing (fn [m [k ref]]
                                           (assoc! m k (conj (get m k #{}) ref)))
                                         persistent!)
                             (transient {})
                             refs))
                         match))))
           (match-word db needle (fn [word]
                                   (sorted-map (get needle-order needle) word))))
         ;; Join the words in the order they appears in the string
         (into []
               (map (fn [[words refs]]
                      [(str/join " " (distinct (vals words))) refs]))))))

(defn match-next-word [db matches]
  (let [match-ref-fn (partial-match-ref db nil nil)]
    (->> (for [[word refs] matches
               :let [used-words (into #{} (str/split word #" "))]
               ref refs
               :let [words (match-ref-fn ref)]
               w words
               :when (and (some? w) (not (contains? used-words w)))]
           [word [w ref]])
         (reduce (fn [m [word [w ref]]]
                   (assoc! m [word w] ((fnil conj #{}) (get m [word w]) ref)))
                 (transient {}))
         (persistent!)
         (into [] (map (fn [[words refs]]
                         [(str/join " " words) refs]))))))

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

  (into []
        (map (juxt identity
                   (fn [search]
                     (prn "Running search: " search)
                     (time (count (match-string (:db stuff) search))))))
    ["acid"
     "acid bath"
     "ac bath"
     "a bat"
     "a ba"
     "a b"
     "a b c"
     "a b c d"])
  )
