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
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/store (om/get-query common.store/Store)}])
  Object
  (render [this]
    (let [{:keys [release? proxy/store proxy/navbar]} (om/props this)]
      (dom/html
        {:lang "en"}
        (apply dom/head nil (common/head release?))
        (dom/body
          nil
          (dom/div
            {:id "sulo-store"
             :className "page-container"}
            (nav/->Navbar navbar)
            (dom/div {:className "page-content" :id "sulo-store-container"}
              (common.store/->Store store))
            (common/footer nil))

          ;(dom/script {:src "https://webrtc.github.io/adapter/adapter-latest.js"})
          (dom/script {:src "/lib/videojs/video.min.js"})
          (dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
          (dom/script {:src "/lib/videojs/videojs.hls.min.js"})
          (dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})

          (common/inline-javascript ["env.web.main.runnavbar()"])
          (common/inline-javascript ["env.web.main.runstream()"]))))))
