(ns eponai.common.api.products
  (:require [clojure.string]
            [clojure.set :as set]
            [eponai.common.database :as db]))

(def category-path-separator " ")
(def category-name-separator "-")

(def eq-or-child-category-rule
  '[[(eq-or-child-category? ?c ?x)
     [(identity ?c) ?x]]
    [(eq-or-child-category? ?c ?recur)
     [?c :category/children ?child]
     (eq-or-child-category? ?child ?recur)]])

(defn find-by-category [category-path]
  {:where   '[[?category :category/path ?path]
              (eq-or-child-category? ?category ?c)
              [?e :store.item/category ?c]]
   :symbols {'?path category-path}
   :rules   eq-or-child-category-rule})

(defn find-all []
  {:where '[[?e :store.item/name]]})

(defn age-gender-filter [age-group gender]
  (let [genders {:women #{"women" "girls" "unisex-adult" "unisex-kids"}
                 :men   #{"men" "boys" "unisex-adult" "unisex-kids"}}
        ages {:adult #{"women" "men" "unisex-adult"}
              :kids  #{"boys" "girls" "unisex-kids"}}
        path-filter-query (fn [allowed-paths]
                            {:fulltext {:attr   :category/path
                                        :arg    '?allowed-path
                                        :return '[[?category ?path]]}
                             :where    '[:fulltext
                                         [?e :store.item/category ?category]]
                             :symbols  {'[?allowed-path ...] (seq allowed-paths)}
                             :rules    eq-or-child-category-rule})]
    (condp = [(= :any gender) (= :any age-group)]
      [true true] (find-all)
      [false true] (path-filter-query (seq (get genders gender)))
      [true false] (path-filter-query (seq (get ages age-group)))
      [false false] (let [allowed (set/intersection (get genders gender) (get ages age-group))]
                      (path-filter-query allowed)))))

(defn find-with-filter [browse-filter]
  (condp = browse-filter
    "women" (age-gender-filter :adult :women)
    "men"   (age-gender-filter :adult :men)
    "adult" (age-gender-filter :adult :any)

    "girls" (age-gender-filter :kids :women)
    "boys"  (age-gender-filter :kids :men)
    "kids"  (age-gender-filter :kids :any)
    (throw (ex-info (str "Unknown browse filter: " browse-filter) {:browse-filter browse-filter}))))