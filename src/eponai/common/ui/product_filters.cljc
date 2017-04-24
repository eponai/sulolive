(ns eponai.common.ui.product-filters
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.client.routes :as routes]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]))


(defn submenu [component category]
  (let [{:keys [active]} (om/get-state component)
        {:category/keys [children]} category]
    (when (not-empty children)
      (menu/vertical
        (cond->> {:classes [:nested :submenu]}
                 (= active (:category/path category))
                 (css/add-class ::css/is-active))
        (map (fn [c]
               (let [{:category/keys [path label]} c]
                 (menu/item {:classes [:is-submenu-item]}
                            (dom/a {:href (routes/url :products/categories {:category path})
                                    :onClick #(.selected-category component c)}
                                   (dom/span nil label)))))
             children)))))

(defui ProductFilters
  static om/IQuery
  (query [_]
    ['{:query/top-categories [:category/label :category/path {:category/children ...}]}])
  Object
  (toggle-filter [this category]
    #?(:cljs
       (let [{:keys [active]} (om/get-state this)]
         (if (= active category)
           (om/update-state! this dissoc :active)
           (om/update-state! this assoc :active category)))))
  (selected-category [this category]
    #?(:cljs
       (let [{:keys [on-change]} (om/get-computed this)]
         (when on-change
           (on-change category)))))

  (render [this]
    (let [{:keys [active]} (om/get-state this)
          {:query/keys [top-categories]} (om/props this)]
      (dom/div
        {:id "product-filters"}
        (dom/h3 (css/add-class :header) "Filter by Category")
        (dom/div
          nil
          (menu/vertical
            (->> {:data-accordion-menu true}
                 (css/add-class :navigation))
            (map (fn [c]
                   (let [{:category/keys [label path children]} c]
                     (menu/item
                       (->> {:aria-expanded (some? active)})
                       (dom/div nil
                                (dom/a {:href (routes/url :products/categories {:category path})
                                        :onClick #(.selected-category this c)}
                                       (dom/strong nil label))
                                (if (not-empty children)
                                  (dom/a
                                    (->> {:href    "#"
                                          :onClick #(.toggle-filter this path)})
                                    (if (= active path)
                                      (dom/span nil "+")
                                      (dom/span nil "-")))
                                  (dom/a nil)))
                       (submenu this c))))
                 top-categories)))))))

(def ->ProductFilters (om/factory ProductFilters))