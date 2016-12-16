(ns eponai.server.ui.streams
  (:require
    [eponai.common.ui.streams :as streams]
    [eponai.server.ui.common :as common]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))


(defui Streams
  Object
  (render [this]
    (let [{:keys [release? :eponai.server.ui/render-component-as-html]} (om/props this)]
      (dom/html
        {:lang "en"}
        (apply dom/head nil (common/head release?))
        (dom/body
          nil
          (dom/div {:height "100%" :id "the-sulo-app"}
            (render-component-as-html streams/Streams)
            ;(common.store/->Store store)
            ;(nav/navbar navbar)
            ;(dom/div {:className "page-content" :id "sulo-store-container"}
            ;  (common.store/->Store store))
            ;(common/footer nil)
            )

          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})
          ;(dom/script {:src "https://cdn.auth0.com/js/lock/10.6/lock.min.js"})
          (dom/script {:src "https://cdn.auth0.com/js/lock-passwordless-2.2.min.js"})

          (common/inline-javascript ["env.web.main.runstreams()"]))))))
