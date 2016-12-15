(ns eponai.server.ui.index
  (:require
    [eponai.server.ui.common :as clj-common :refer [text-javascript]]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.index :as index]
    [eponai.server.ui.common :as common]))

(defui Index
  Object
  (render [this]
    (let [{:keys [release? :eponai.server.ui/render-component-as-html]} (om/props this)]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (clj-common/head release?))
        (dom/body
          nil
          (dom/div {:id "sulo-index" :className "sulo-page"}
            (render-component-as-html index/Index))
          ;(common/page-container
          ;  {:navbar navbar}
          ;  (dom/div
          ;    {:id        "sulo-start"
          ;     :className "page-container"}
          ;    (nav/navbar navbar)
          ;
          ;
          ;    (cljc-common/footer nil)))

          (common/auth0-lock-passwordless release?)
          (dom/script {:src  (clj-common/budget-js-path release?)
                       :type clj-common/text-javascript})
          (clj-common/inline-javascript ["env.web.main.runindex()"])
          )))))