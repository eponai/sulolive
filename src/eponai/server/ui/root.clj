(ns eponai.server.ui.root
  (:require
    [eponai.server.ui.common :as common]
    [om.next :as om :refer [defui]]
    [om.dom :as dom]))

(defui Root
  Object
  (render [this]
    (let [{:keys [release? component]} (om/props this)
          component-id (om/get-params component)]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (common/head release?))

        (dom/body
          nil
          (dom/div
            {:className "page-container"
             :id component-id}
            (common/navbar nil)
            (dom/div {:className "page-content"}
              ((om/factory component) (om/props this)))
            (common/footer nil))

          ;(dom/script {:src "https://webrtc.github.io/adapter/adapter-latest.js"})
          (dom/script {:src "/lib/videojs/video.min.js"})
          (dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
          (dom/script {:src "/lib/videojs/videojs.hls.min.js"})
          (dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})

          (common/inline-javascript ["env.web.main.runstream()"]))))))