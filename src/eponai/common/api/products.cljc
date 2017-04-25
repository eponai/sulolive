(ns eponai.common.api.products
  (:require [clojure.string]
            [clojure.set :as set]
            [eponai.common.database :as db]))

(def category-path-separator " ")
(def category-name-separator "-")

(def eq-or-child-category-rule
  '[[(eq-or-child-category? ?c ?x)
     [(= ?c ?x)]]
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

(defn find-with-category-names [top-category {:keys [sub-category sub-sub-category]}]
  (let [smallest-cat (or (when sub-sub-category '?sub-sub)
                         (when sub-category '?sub)
                         '?top)]
    (db/merge-query
      (cond-> {:where   '[[?top :category/path ?top-name]
                          [?e :store.item/category ?item-category]]
               :symbols {'?top-name top-category}}
              (some? sub-category)
              (db/merge-query {:where   '[[?top :category/children ?sub]
                                          [?sub :category/name ?sub-name]]
                               :symbols {'?sub-name sub-category}})
              (some? sub-sub-category)
              (db/merge-query {:where   '[[?sub :category/children ?sub-sub]
                                          [?sub-sub :category/name ?sub-sub-name]]
                               :symbols {'?sub-sub-name sub-sub-category}}))
      {:where [(list 'eq-or-child-category? smallest-cat '?item-category)]
       :rules eq-or-child-category-rule})))

(defn age-gender-filter [age-group gender]
  (let [genders {:women #{"women" "girls" "unisex-adult" "unisex-kids"}
                 :men   #{"men" "boys" "unisex-adult" "unisex-kids"}}
        ages {:adult #{"women" "men" "unisex-adult"}
              :kids  #{"boys" "girls" "unisex-kids"}}
        path-filter-query (fn [allowed-paths]
                            {:fulltext [{:id     :allowed-path-search
                                         :attr   :category/path
                                         :arg    '?allowed-path
                                         :return '[[?filter-category]]}]
                             :where    '[{:fulltext-id :allowed-path-search}
                                         [?e :store.item/category ?filter-category]]
                             :symbols  {'[?allowed-path ...] (seq allowed-paths)}})]
    (condp = [(= :any gender) (= :any age-group)]
      [true true] (find-all)
      [false true] (path-filter-query (seq (get genders gender)))
      [true false] (path-filter-query (seq (get ages age-group)))
      [false false] (let [allowed (set/intersection (get genders gender) (get ages age-group))]
                      (path-filter-query allowed)))))



(defn find-with-browse-filter [browse-filter {:keys [top-category sub-category sub-sub-category]}]
  (let [browse-filter-query (condp = browse-filter
                              "women" (age-gender-filter :adult :women)
                              "men" (age-gender-filter :adult :men)
                              "unisex" (age-gender-filter :adult :any)

                              "girls" (age-gender-filter :kids :women)
                              "boys" (age-gender-filter :kids :men)
                              "kids" (age-gender-filter :kids :any)
                              (throw (ex-info (str "Unknown browse filter: " browse-filter) {:browse-filter browse-filter})))]
    (db/merge-query browse-filter-query
                    (cond-> {}
                            (some? top-category)
                            (db/merge-query {:where   '[[?top :category/name ?top-name]
                                                        [?top :category/children ?child]
                                                        (eq-or-child-category? ?child ?filter-category)]
                                             :symbols {'?top-name top-category}
                                             :rules   eq-or-child-category-rule})
                            (some? sub-category)
                            (db/merge-query {:where   '[[?filter-category :category/children ?sub]
                                                        [?sub :category/name ?sub-name]]
                                             :symbols {'?sub-name sub-category}})
                            (some? sub-sub-category)
                            (db/merge-query {:where   '[[?sub :category/children ?sub-sub]
                                                        [?sub-sub :category/name ?sub-sub-name]]
                                             :symbols {'?sub-sub-name sub-sub-category}})))))