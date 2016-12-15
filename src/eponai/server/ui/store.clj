(ns eponai.server.ui.store
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.server.parser.read :as read]
    [eponai.server.ui.common :as common]
    [eponai.common.ui.store :as common.store]
    [eponai.common.ui.common :as cljc-common]
    [eponai.common.ui.navbar :as nav]))

(defui Store
  Object
  (render [this]
    (let [{:keys [release? :eponai.server.ui/render-component-as-html]} (om/props this)]
      (dom/html
        {:lang "en"}
        (apply dom/head nil (common/head release?))
        (dom/body
          nil
          (dom/div
            {:id "sulo-store" :className "sulo-page"}
            (render-component-as-html common.store/Store)
            ;(nav/navbar navbar)
            ;(dom/div {:className "page-content" :id "sulo-store-container"}
            ;  (common.store/->Store store))
            ;(common/footer nil)
            )

          (common/red5pro-script-tags release?)
          (common/auth0-lock-passwordless release?)
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})
          ;(dom/script {:src "https://cdn.auth0.com/js/lock/10.6/lock.min.js"})

          (common/inline-javascript ["env.web.main.runstore()"]))))))
