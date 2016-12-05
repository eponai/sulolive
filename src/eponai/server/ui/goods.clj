(ns eponai.server.ui.goods
  (:require
    [eponai.server.ui.common :as common]
    [eponai.server.ui.store :as store]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Goods
  Object
  (render [this]
    (let [{:keys [release?]} (om/props this)
          goods (mapcat :goods store/stores)]
      (prn "PROPS: " (om/props this))
      (dom/html
        {:lang "en"}

        (apply dom/head nil (common/head release?))

        (dom/body
          {:id "sulo-goods"}
          (common/navbar nil)

          (dom/div {:className "page-content"}
            (dom/div {:className "items"}
              (apply dom/div {:className "content-items-container row small-up-2 medium-up-4"}
                     (map (fn [p]
                            (common/product-element p))
                          (shuffle goods)))))

          ;(dom/script {:src "https://webrtc.github.io/adapter/adapter-latest.js"})
          (dom/script {:src "/lib/videojs/video.min.js"})
          (dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
          (dom/script {:src "/lib/videojs/videojs.hls.min.js"})
          (dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})

          (common/inline-javascript ["env.web.main.runstream()"]))))))
