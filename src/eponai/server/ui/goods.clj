(ns eponai.server.ui.goods
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.goods :as goods]
    [eponai.server.ui.common :as common]))

(defui Goods
  Object
  (render [this]
    (let [{:keys [release? :eponai.server.ui/render-component-as-html]} (om/props this)]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (common/head release?))

        (dom/body
          nil
          (dom/div
            {:id "sulo-items" :className "sulo-page"}
            (render-component-as-html goods/Goods))

          ;(dom/script {:src "https://webrtc.github.io/adapter/adapter-latest.js"})
          ;(dom/script {:src "/lib/videojs/video.min.js"})
          ;(dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
          ;(dom/script {:src "/lib/videojs/videojs.hls.min.js"})
          ;(dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})
          (common/auth0-lock-passwordless release?)
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})

          (common/inline-javascript ["env.web.main.rungoods()"])
          )))))
