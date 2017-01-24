(ns eponai.common.ui.settings
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    ;[om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]))


(defn menu-tab-item [component k n]
  (let [{:keys [selected-tab]} (om/get-state component)]
    (menu/item-tab {:active?  (= selected-tab k)
                    :on-click #(om/update-state! component assoc :selected-tab k)} n)))

(defn tab-panel [component k & content]
  (let [{:keys [selected-tab]} (om/get-state component)]
    (apply dom/div (cond->> (css/add-class :tabs-panel)
                      (= selected-tab k)
                      (css/add-class ::css/is-active))
             content)))
(defui Settings
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}])
  Object
  (initLocalState [_]
    {:selected-tab :general})
  (render [this]
    (let [{:keys [query/items proxy/navbar]} (om/props this)
          {:keys [selected-tab]} (om/get-state this)]
      (dom/div
        {:id "sulo-settings" :classes [:sulo-page]}
        (common/page-container
          {:navbar navbar}
          (dom/div
            (->> (css/grid-row)
                 css/grid-column)
            (dom/h3 nil "Account Settings")
            (menu/tabs
              nil
              (menu-tab-item this :general "General")
              (menu-tab-item this :shipping "Shipping")
              (menu-tab-item this :payment "Payment"))


            (dom/div
              (css/add-class :tabs-content)
              (tab-panel this :general "This is tab  general")
              (tab-panel this :shipping "This is tab shipping")
              (tab-panel this :payment "This is tab payment"))))))))

(def ->Settings (om/factory Settings))

