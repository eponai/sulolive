(ns eponai.common.ui.product-filters
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.client.routes :as routes]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.api.products :as products]
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]))

(defn submenu [component {:keys [label]}]
  (let [{:keys [active]} (om/get-state component)
        db (db/to-db component)
        gender? (products/gender-category? {:category/name label})
        nav-tree (products/browse-navigation-tree db
                                                  [:category/name :category/label :category/path]
                                                  gender?
                                                  (if gender?
                                                    {:sub-category label}
                                                    {:top-category label}))
        {:category/keys [children]} nav-tree]
    (debug "label: " label)
    (debug "nav-tree: " nav-tree)
    (when (not-empty children)
      (menu/vertical
        (cond->> {:classes [:nested :submenu]}
                 (= active label)
                 (css/add-class ::css/is-active))
        (map (fn [c]
               (let [{:category/keys [href]} c]
                 (menu/item {:classes [:is-submenu-item]}
                            (dom/a {:href    href
                                    :onClick #(.clicked-category component)}
                                   (dom/span nil (products/category-display-name c))))))
             children)))))

(comment
  (let [db (db/to-db this)
        gender? (products/gender-category? {:category/name label})
        nav-tree (products/browse-navigation-tree db
                                                  [:db/id :category/name :category/label :category/path]
                                                  gender?
                                                  (if gender?
                                                    {:sub-category label}
                                                    {:top-category label}))]))

(defui ProductFilters
  static om/IQuery
  (query [_]
    [{:query/top-nav-categories [:label :href]}])
  Object
  (toggle-filter [this category]
    (let [{:keys [active]} (om/get-state this)]
      (if (= active category)
        (om/update-state! this dissoc :active)
        (om/update-state! this assoc :active category))))
  (clicked-category [this]
    (let [{:keys [on-click]} (om/get-computed this)]
      (when on-click
        (on-click))))

  (render [this]
    (let [{:keys [active]} (om/get-state this)
          {:query/keys [top-nav-categories]} (om/props this)]
      (dom/div
        {:id "product-filters"}
        (dom/h3 (css/add-class :header) "Filter by Category")
        (dom/div
          nil
          (menu/vertical
            (->> {:data-accordion-menu true}
                 (css/add-class :navigation))
            (map (fn [{:keys [label href] :as top-nav-cat}]
                   (let []
                     (menu/item
                       {:aria-expanded (some? active)}
                       (dom/div nil
                                (dom/a {:href href
                                        :onClick #(.clicked-category this)}
                                       (dom/strong nil label))
                                (dom/a
                                  (->> {:href    "#"
                                        :onClick #(.toggle-filter this label)})
                                  (if (= active label)
                                    (dom/span nil "+")
                                    (dom/span nil "-"))))
                       (submenu this top-nav-cat))))
                 top-nav-categories)))))))

(def ->ProductFilters (om/factory ProductFilters))