(ns eponai.common.ui.settings
  (:require
    [eponai.common.ui.dom :refer [div input span]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]
    [taoensso.timbre :refer [debug]]))

(defui Settings
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     :query/auth])
  Object
  (initLocalState [_]
    {:selected-tab :general})
  (render [this]
    (let [{:keys [query/auth proxy/navbar]} (om/props this)
          {:keys [selected-tab]} (om/get-state this)]
      (debug "AUTH: " auth)
      (div
        {:id "sulo-settings" :classes [:sulo-page]}
        (common/page-container
          {:navbar navbar}
          (div
            (->> (css/grid-row)
                 css/grid-column)
            (dom/h3 nil "Account Settings")
            (div (css/callout)
                 (dom/h4 nil "General")
                 (div (->> (css/grid-row)
                           (css/align :center))
                      (div (->> (css/grid-column)
                                (css/grid-column-size {:small 12 :medium 8}))
                           (dom/label nil (dom/span nil "Email"))
                           (dom/input #js {:type "email"}))))

            (div (css/callout)
                 (dom/h4 nil "Shipping Adresses"))

            (div (css/callout)
                 (dom/h4 nil "Payment Options"))
            ;(menu/tabs
            ;  nil
            ;  (menu-tab-item this :general "General")
            ;  (menu-tab-item this :shipping "Shipping")
            ;  (menu-tab-item this :payment "Payment"))


            ;(div
            ;  (css/add-class :tabs-content)
            ;  (tab-panel this :general "This is tab  general")
            ;  (tab-panel this :shipping "This is tab shipping")
            ;  (tab-panel this :payment "This is tab payment"))
            ))))))

(def ->Settings (om/factory Settings))

