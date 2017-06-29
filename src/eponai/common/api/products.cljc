(ns eponai.common.api.products
  (:require [clojure.string :as str]
            [eponai.common.routes :as routes]
            [eponai.common.database :as db]
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

(def eq-or-child-category-rule
  '[[(eq-or-child-category? ?c ?x)
     [(identity ?c) ?x]]
    [(eq-or-child-category? ?c ?recur)
     [?c :category/children ?child]
     (eq-or-child-category? ?child ?recur)]])

(defn find-by-category [category-path]
  {:where   '[[?category :category/path ?path]
              (eq-or-child-category? ?category ?c)
              [?status :status/type :status.type/open]
              [?s :store/status ?status]
              [?s :store/profile ?p]
              [?p :store.profile/photo _]
              [?s :store/items ?e]
              [?e :store.item/category ?c]]
   :symbols {'?path category-path}
   :rules   eq-or-child-category-rule})

(defn find-all [locality]
  {:where '[[?s :store/locality ?l]
            [?status :status/type :status.type/open]
            [?s :store/status ?status]
            [?s :store/profile ?p]
            [?p :store.profile/photo _]
            [?s :store/items ?e]
            ;[?e :store.item/uuid _]
            ]
   :symbols {'?l (:db/id locality)}})

(defn find-with-search
  [locality search]
  {:where    '[[?s :store/items ?e]
               [?s :store/locality ?l]
               [?s :store/status ?status]
               [?status :status/type :status.type/open]
               [?s :store/profile ?p]
               [?p :store.profile/photo _]]
   :symbols  {'?search search
              '?l      (:db/id locality)}
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

(defn find-with-category-names [locality category-names]
  (db/merge-query (category-names-query category-names)
                  {:where [(list 'eq-or-child-category?
                                 (smallest-category category-names)
                                 '?item-category)
                           '[?s :store/locality ?l]
                           '[?s :store/status ?status]
                           '[?status :status/type :status.type/open]
                           '[?s :store/profile ?p]
                           '[?p :store.profile/photo _]
                           '[?s :store/items ?e]
                           '[?e :store.item/category ?item-category]]
                   :symbols {'?l (:db/id locality)}
                   :rules eq-or-child-category-rule}))

;; Navigation bs

(defn browse-navigation-tree [db query gender-route? category-names]
  ;; There's something symetrical going on here, but I haven't quite figured it out. Sorry.
  (let [{:keys [top-category sub-category]} category-names
        cat-query (category-names-query (dissoc category-names :sub-sub-category))
        find-top (db/merge-query cat-query {:where '[[(identity ?top) ?e]]})
        find-sub (db/merge-query cat-query {:where '[[(identity ?sub) ?e]]})
        find-sub-sub (db/merge-query cat-query {:where '[[?sub :category/children ?e]]})
        ;; deduping by name because it doesn't matter if we get multiple unisex shoes and women's shoes.
        ;; In query/browse-items we're getting correct items anyway. (?)
        pull-distinct-by-name (fn [entity-query]
                                (->> (db/pull-all-with db query entity-query)
                                     (into [] (medley/distinct-by :category/name))))
        add-routes (fn [route params category-level children]
                     (into [] (map #(assoc % :category/href (routes/path route (assoc params category-level (:category/name %)))))
                           children))]
    (if gender-route?
      {:category/href     (routes/path :browse/gender {:sub-category sub-category})
       :category/name     sub-category
       :category/label    (str/capitalize sub-category)
       :category/children (->> (if (nil? top-category)
                                 (pull-distinct-by-name find-top)
                                 [(-> (db/pull-one-with db query find-top)
                                      (assoc :category/children (->> (pull-distinct-by-name find-sub-sub)
                                                                     (add-routes :browse/gender+top+sub-sub {:sub-category sub-category
                                                                                                             :top-category top-category}
                                                                                 :sub-sub-category))))])
                               (add-routes :browse/gender+top {:sub-category sub-category} :top-category))}
      (-> (db/pull-one-with db query find-top)
          (assoc :category/href (routes/path :browse/category {:top-category top-category}))
          (assoc :category/children (->> (if (nil? sub-category)
                                           (pull-distinct-by-name find-sub)
                                           [(-> (db/pull-one-with db query find-sub)
                                                (assoc :category/children (->> (pull-distinct-by-name find-sub-sub)
                                                                               (add-routes :browse/category+sub+sub-sub {:sub-category sub-category
                                                                                                                         :top-category top-category}
                                                                                           :sub-sub-category))))])
                                         (add-routes :browse/category+sub {:top-category top-category} :sub-category)))))))