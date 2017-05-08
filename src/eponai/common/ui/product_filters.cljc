(ns eponai.common.ui.product-filters
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]
    [taoensso.timbre :refer [debug]]))

(defn submenu [component {:category/keys [name children]}]
  (let [{:keys [active]} (om/get-state component)]
    (when (seq children)
      (menu/vertical
        (cond->> {:classes [:nested :submenu]}
                 (= active name)
                 (css/add-class ::css/is-active))
        (map (fn [{:category/keys [href label]}]
               (menu/item {:classes [:is-submenu-item]}
                          (dom/a {:href    href
                                  :onClick #(.clicked-category component)}
                                 (dom/span nil label))))
             children)))))

(defui ProductFilters
  static om/IQuery
  (query [_]
    [{:query/navigation [:category/name :category/label :category/path :category/href]}])
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
          {:query/keys [navigation]} (om/props this)]
      (dom/div
        {:id "product-filters"}
        (dom/h3 (css/add-class :header) "Filter by Category")
        (dom/div
          nil
          (menu/vertical
            (->> {:data-accordion-menu true}
                 (css/add-class :navigation))
            (map (fn [{:category/keys [name label href] :as top-nav-cat}]
                   (let []
                     (menu/item
                       {:aria-expanded (some? active)}
                       (dom/div nil
                                (dom/a {:href href
                                        :onClick #(.clicked-category this)}
                                       (dom/strong nil label))
                                (dom/a
                                  (->> {:href    "#"
                                        :onClick #(.toggle-filter this name)})
                                  (if (= active name)
                                    (dom/span nil "+")
                                    (dom/span nil "-"))))
                       (submenu this top-nav-cat))))
                 navigation)))))))

(def ->ProductFilters (om/factory ProductFilters))