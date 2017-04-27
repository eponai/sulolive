(ns eponai.common.api.products
  (:require [clojure.string :as str]
            [eponai.common.routes :as routes]
            [eponai.common.database :as db]
            [taoensso.timbre :refer [debug warn]]))

(def category-path-separator " ")
(def category-path-separator-pattern (re-pattern category-path-separator))
(def category-name-separator "-")

(def gender-categories #{"women" "men" "boys" "girls" "unisex-adult" "unisex-kids"})
(def gender-category? (comp gender-categories :category/name))

(defn category-display-name [{:category/keys [label] :as c}]
  (if (gender-category? c)
    (str/capitalize (:category/name c))
    label))

(defn category-names [{:category/keys [path] :as c}]
  (when-not path
    (warn "No path in category: " c " when creating category names"))
  (->> (str/split path category-path-separator-pattern)
       (zipmap [:top-category :sub-category :sub-sub-category])))

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
  (let [unisex-categories {"women"        ["unisex-adult"]
                           "men"          ["unisex-adult"]
                           "boys"         ["unisex-kids"]
                           "girls"        ["unisex-kids"]
                           "unisex-adult" ["women" "men"]
                           "unisex-kids"  ["boys" "girls"]}]
    (cons category-name (get unisex-categories category-name))))

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
