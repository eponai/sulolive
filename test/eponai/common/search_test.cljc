(ns eponai.common.search-test
  (:require
    [clojure.test :as test :refer [deftest]]
    [datascript.core :as datascript]
    [eponai.common.search :as search]
    [eponai.common.database :as db]
    [clojure.string :as str]
    [taoensso.timbre :refer [debug]]))

(defn search-conn-with-names [& names]
  (-> (db/db (datascript/create-conn))
      (datascript/db-with (into []
                                (map #(hash-map :name %))
                                names))
      (search/conn-with-index :name)))

(deftest test-match-string
  (let [conn (search-conn-with-names "Petter Eriksson"
                                     "Perre")
        db (db/db conn)]
    (test/are [search matches]
              (= (into #{} (map str/lower-case) matches)
                 (into #{}
                       (map first)
                       (search/match-string db search)))
      "p" ["Petter" "Perre"]
      "pe" ["Petter" "Perre"]
      "pet" ["Petter"]
      "x" []
      "er" ["Eriksson"]
      "p er" ["Petter Eriksson"])))

(deftest test-match-next-word
  (let [conn (search-conn-with-names "Petter Eriksson"
                                     "Perre"
                                     "Petter Gren")
        db (db/db conn)]
    (test/are [search matches]
              (= (into #{} (map str/lower-case) matches)
                 (into #{}
                       (map first)
                       (->> (search/match-string db search)
                            (search/match-next-word db))))
      "p" ["Petter Eriksson"
           "Petter Gren"]
      "pe" ["Petter Eriksson"
            "Petter Gren"]
      "pet" ["Petter Eriksson"
             "Petter Gren"]
      "per" []
      "g" ["Gren Petter"]
      "e" ["Eriksson Petter"]
      "p g" []
      "g p" [])))

(defn db-without-changes
  "Takes changes in the form of [[<word>]] or [[<word> false]], meaning retract word.
  Performs each 'change' as a transaction the vector order."
  [db changes]
  (loop [db db changes changes]
    (if-let [[[name added] & changes] (seq changes)]
      (let [entities (if-let [matches (seq (->> (search/match-string db (str/lower-case name))
                                                (filter (fn [[word]]
                                                          (= (str/lower-case word)
                                                             (str/lower-case name))))))]
                       (mapcat (fn [[_ refs]]
                                 (->> refs
                                      (map (fn [ref]
                                             (cond-> {:name name :added added}
                                                     (false? added)
                                                     (assoc :db/id ref))))))
                            matches)
                       [{:name  name
                         :added added}])]
        (recur (search/transact db (sequence
                                     (search/entities-by-attr-tx :name)
                                     entities))
               changes))
      db)))

(deftest test-adds-retracts
  (let [conn (search-conn-with-names "Petter Eriksson"
                                     "Perre"
                                     "Petter Gren")
        db (db/db conn)]
    (test/are [changes search refs]
      (= refs
         (->> (db-without-changes db changes)
              (#(search/match-string % search))
              (into #{} (mapcat second))
              (count)))
      [["foo"]] "foo" 1
      [] "per" 1
      [["Perre" false]] "per" 0
      [["Gren" false]] "gr" 0
      [["x"]] "x" 1
      [["g"]] "g" 2)))

(deftest test-gc
  (let [conn (search-conn-with-names "Petter Eriksson"
                                     "Perre"
                                     "Petter Gren")
        db (db/db conn)]
    (test/are [changes search matches-before matches-after]
              (and (= matches-before (count (search/match-string db
                                                                 search)))
                   (= matches-after (count (search/match-string (-> db
                                                                    (db-without-changes changes)
                                                                    (search/gc))
                                                                search))))
      [["foo"]] "fo" 0 1
      [["Gren" false]] "gren" 1 0
      [["a"]] "a" 0 1
      [["a"] ["a" false]] "a" 0 0
      [["a"] ["aa"]] "a" 0 2
      [["a"] ["aa"] ["aa" false]] "a" 0 1
      ;; Tests that retracting single char "a" doesn't remove all matches for #"a.+"
      [["a"] ["aa"] ["a" false]] "a" 0 1
      )))
