(ns eponai.server.ui.product
  (:require
    [eponai.common.ui.common :as cljc-common]
    [eponai.server.ui.common :as common]
    [eponai.common.ui.product :as cljc-product]
    [eponai.server.parser.read :as store]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]))

(defui Product
  Object
  (render [this]
    (let [{:keys [release? :eponai.server.ui/render-component-as-html]} (om/props this)]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (common/head release?))

        (dom/body
          nil
          (dom/div
            {:id "sulo-product-page" :className "sulo-page"}
            (render-component-as-html cljc-product/ProductPage)
            ;(nav/navbar navbar)

            ;(dom/div {:className "page-content" :id "sulo-product-container"}
            ;  (cljc-product/->Product item))

            ;(cljc-common/footer nil)
            )

          ;(dom/script {:src "/lib/videojs/video.min.js"})
          ;(dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
          ;(dom/script {:src "/lib/videojs/videojs.hls.min.js"})
          ;(dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})
          (common/auth0-lock-passwordless release?)
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})


          (common/inline-javascript ["env.web.main.runproduct()"])
          )))))

