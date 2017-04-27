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

(defn- normalize-gender [category-name]
  (let [unisex-categories {"women" "unisex-adult"
                           "men"   "unisex-adult"
                           "boys"  "unisex-kids"
                           "girls" "unisex-kids"}]
    (cons category-name (some-> (get unisex-categories category-name)
                                (vector)))))

(defn category-names-query [{:keys [top-category sub-category sub-sub-category]}]
  {:pre [(or top-category sub-category sub-sub-category)]}
  (cond-> {}
          (some? top-category)
          (db/merge-query {:where   '[[?top :category/path ?top-name]]
                           :symbols {'?top-name top-category}})
          (some? sub-category)
          (db/merge-query {:where   '[[?top :category/children ?sub]
                                      [?sub :category/name ?sub-name]]
                           :symbols {'[?sub-name ...] (normalize-gender sub-category)}})
          (some? sub-sub-category)
          (db/merge-query {:where   '[[?sub :category/children ?sub-sub]
                                      [?sub-sub :category/name ?sub-sub-name]]
                           :symbols {'[?sub-sub-name ...] (normalize-gender sub-sub-category)}})))

(defn smallest-category [{:keys [top-category sub-category sub-sub-category]}]
  (or (when sub-sub-category '?sub-sub)
      (when sub-category '?sub)
      (when top-category '?top)))

(defn find-with-category-names [category-names]
  (db/merge-query (category-names-query category-names)
                  {:where [(list 'eq-or-child-category?
                                 (smallest-category category-names)
                                 '?item-category)
                           '[?e :store.item/category ?item-category]]
                   :rules eq-or-child-category-rule}))
