(ns eponai.common.api.products
  (:require [clojure.string :as str]
            [eponai.common.routes :as routes]
            [eponai.common.database :as db]
            [eponai.common.database.rules :as db.rules]
            [medley.core :as medley]
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

(defn find-all []
  {:where   '[(listed-store ?s)
              [?s :store/items ?e]
              ;[?e :store.item/uuid _]
              ]
   :rules   [db.rules/listed-store]})

(defn find-with-search
  [search]
  {:where    '[[?s :store/items ?e]
               (listed-store ?s)]
   :symbols  {'?search search}
   :rules    [db.rules/listed-store]
   :fulltext [{:attr   :store.item/name
               :arg    '?search
               :return '[[?e ?item-name _ ?score]]}]})

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
          (db/merge-query {:where   '[[?sub :category/name ?sub-name]
                                      [?top :category/children ?sub]]
                           :symbols {'[?sub-name ...] (normalize-gender sub-category)}})
          (some? sub-sub-category)
          (db/merge-query {:where   '[[?sub-sub :category/name ?sub-sub-name]
                                      [?sub :category/children ?sub-sub]]
                           :symbols {'[?sub-sub-name ...] (normalize-gender sub-sub-category)}})))

(defn smallest-category [{:keys [top-category sub-category sub-sub-category]}]
  (or (when sub-sub-category '?sub-sub)
      (when sub-category '?sub)
      (when top-category '?top)))

(defn find-with-category-names [{:keys [top-category sub-category] :as category-names}]
  (cond-> {:where   '[(listed-store ?s)
                      [?s :store/items ?e]]
           :rules   [db.rules/listed-store]}
          (or (some? top-category) (some? sub-category))
          (db/merge-query (db/merge-query (category-names-query category-names)
                                          {:where ['[?e :store.item/category ?item-category]
                                                   (list 'category-or-child-category
                                                         (smallest-category category-names)
                                                         '?item-category)]
                                           :rules [db.rules/category-or-child-category]}))))
