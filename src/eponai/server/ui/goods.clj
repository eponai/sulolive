(ns eponai.server.ui.goods
  (:require
    [eponai.common.ui.common :as cljc-common]
    [eponai.server.ui.common :as clj-common]
    [eponai.server.parser.read :as store]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.goods :as goods]))

(defui Goods
  static om/IQuery
  (query [this]
    [{:proxy/goods (om/get-query goods/Goods)}])
  Object
  (render [this]
    (let [{:keys [release? proxy/goods]} (om/props this)]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (clj-common/head release?))

        (dom/body
          nil
          (dom/div
            {:id "sulo-items" :className "sulo-page"}
            (goods/->Goods goods))

          ;(dom/script {:src "https://webrtc.github.io/adapter/adapter-latest.js"})
          ;(dom/script {:src "/lib/videojs/video.min.js"})
          ;(dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
          ;(dom/script {:src "/lib/videojs/videojs.hls.min.js"})
          ;(dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})
          (dom/script {:src "https://cdn.auth0.com/js/lock-passwordless-2.2.min.js"})
          (dom/script {:src  (clj-common/budget-js-path release?)
                       :type clj-common/text-javascript})

          (clj-common/inline-javascript ["env.web.main.rungoods()"])
          )))))
