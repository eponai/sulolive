(ns eponai.server.ui.index
  (:require
    [eponai.common.ui.common :as cljc-common]
    [eponai.server.parser.read :as store]
    [eponai.server.ui.common :as clj-common :refer [text-javascript]]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.index :as index]))

(defui Index
  static om/IQuery
  (query [this]
    [{:proxy/index (om/get-query index/Index)}])
  Object
  (render [this]
    (let [{:keys [release? proxy/index]} (om/props this)]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (clj-common/head release?))
        (dom/body
          nil
          (dom/div {:id "sulo-index" :className "sulo-page"}
            (index/->Index index))
          ;(common/page-container
          ;  {:navbar navbar}
          ;  (dom/div
          ;    {:id        "sulo-start"
          ;     :className "page-container"}
          ;    (nav/navbar navbar)
          ;
          ;
          ;    (cljc-common/footer nil)))

          (dom/script {:src "https://cdn.auth0.com/js/lock-passwordless-2.2.min.js"})
          (dom/script {:src  (clj-common/budget-js-path release?)
                       :type clj-common/text-javascript})
          (clj-common/inline-javascript ["env.web.main.runindex()"])

          )))))