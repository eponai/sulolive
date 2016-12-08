(ns eponai.server.ui.goods
  (:require
    [eponai.common.ui.common :as cljc-common]
    [eponai.server.ui.common :as clj-common]
    [eponai.server.parser.read :as store]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]))

(defui Goods
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/all-items [:item/name
                        :item/price
                        :item/img-src
                        :item/id]}])
  Object
  (render [this]
    (let [{:keys [release? query/all-items proxy/navbar]} (om/props this)]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (clj-common/head release?))

        (dom/body
          nil
          (dom/div
            {:id "sulo-goods"
             :className "page-container"}
            (nav/->Navbar navbar)

            (dom/div {:className "page-content"}
              (dom/div {:className "items"}
                (apply dom/div {:className "row small-up-2 medium-up-4"}
                       (map (fn [p]
                              (cljc-common/product-element p))
                            (shuffle all-items)))))
            (clj-common/footer nil))

          ;(dom/script {:src "https://webrtc.github.io/adapter/adapter-latest.js"})
          ;(dom/script {:src "/lib/videojs/video.min.js"})
          ;(dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
          ;(dom/script {:src "/lib/videojs/videojs.hls.min.js"})
          ;(dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})
          ;(dom/script {:src  (common/budget-js-path release?)
          ;             :type common/text-javascript})

          ;(common/inline-javascript ["env.web.main.runstream()"])
          )))))
