(ns eponai.web.ui.store.shipping
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.components.select :as select]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.common :as common]
    [taoensso.timbre :refer [debug]]))

(defn add-shipping-rule-modal [component]
  (let [on-close #(om/update-state! component dissoc :modal)
        {:query/keys [countries]} (om/props component)
        countries-by-continent (group-by :country/continent countries)
        {:keys [selected-countries]} (om/get-state component)]
    (common/modal
      {:on-close on-close
       :require-close? true}
      (dom/div
        nil
        (dom/span nil "Add modal")
        (select/->SelectOne {:value })
        (dom/select nil
                    (map (fn [[continent cs]]
                           (debug "Entry: " {:continent continent
                                             :countries cs})
                           (dom/optgroup
                             {:label (:continent/name continent)}
                             (map (fn [c]
                                    (dom/option {:value (:country/code c)} (:country/name c)))
                                  cs)))
                         countries-by-continent))))))

(defui StoreShipping
  static om/IQuery
  (query [_]
    [:query/current-route
     {:query/countries [:country/code
                        :country/name
                        {:country/continent [:continent/code
                                             :continent/name]}]}])
  Object
  (render [this]
    (let [{:keys [modal]} (om/get-state this)]
      (dom/div
        {:id "sulo-shipping-settings"}
        (dom/h1 (css/show-for-sr) "Shipping settings")
        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Shipping rules"))
        (cond (= modal :modal/add-shipping-rule)
              (add-shipping-rule-modal this))
        (callout/callout
          nil
          (grid/row
            nil
            (grid/column
              nil
              (dom/p nil (dom/span nil "Create shipping rules to specify where you ship your products and for how much.")))
            (grid/column
              (css/add-class :shrink)
              (button/button
                {:onClick #(om/update-state! this assoc :modal :modal/add-shipping-rule)}
                (dom/span nil "Add shipping rule")))))))))
