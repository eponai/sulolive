(ns eponai.server.ui.product
  (:require
    [eponai.common.ui.common :as cljc-common]
    [eponai.server.ui.common :as common]
    [eponai.server.parser.read :as store]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Product
  static om/IQuery
  (query [_]
    [{:query/item [:item/name :item/price :item/img-src :item/store]}])
  Object
  (render [this]
    (let [{:keys [release? query/item]} (om/props this)
          {:keys [item/price item/store item/img-src]
           item-name :item/name} item]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (common/head release?))

        (dom/body
          nil
          (dom/div
            {:id "sulo-product"
             :className "page-container"}
            (common/navbar nil)

            (dom/div {:className "page-content"}
              (dom/div {:className "row content-items-container store-container align-middle"}
                (dom/div {:className "columns small-4 medium-2"}
                  (dom/div {:className "content-item-thumbnail-container"}
                    (dom/div {:className "content-item-thumbnail" :style {:background-image (str "url(" (:store/photo store) ")")}})))
                (dom/div {:className "columns"}
                  (dom/a {:href (str "/store/" (:store/id store))} (dom/h1 {:className "store-name"} (:store/name store)))
                  (cljc-common/rating-element (:store/rating store) (:store/review-count store))))


              (dom/div {:className "row product-container"}
                (dom/div {:className "column small-12 medium-8 small-order-2 medium-order-1"}
                  (dom/div {:className "product-photo-container row"}
                    (dom/div {:className "product-photo" :style {:background-image (str "url(" img-src ")")}}))
                  (apply dom/div {:className "row small-up-4 medium-up-6"}
                    (map (fn [im]
                           (dom/div {:className "column"}
                             (dom/div {:className "content-item-thumbnail" :style {:background-image (str "url(" im ")")}})))
                         (take 4 (repeat img-src)))))
                (dom/div {:className "column product-info-container small-order-1 medium-order-2"}
                  (dom/div {:className "product-info"}
                    (dom/h2 {:className "product-info-title"} item-name)
                    (dom/h3 {:className "product-info-price"}
                            price))
                  (dom/div {:className "product-action-container clearfix"}
                    (dom/a {:className "button expanded"} "Add to Cart")))))

            (common/footer nil))

          ;(dom/script {:src "/lib/videojs/video.min.js"})
          ;(dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
          ;(dom/script {:src "/lib/videojs/videojs.hls.min.js"})
          ;(dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})
          ;(dom/script {:src  (common/budget-js-path release?)
          ;             :type common/text-javascript})

          ;(common/inline-javascript ["env.web.main.runstream()"])
          )))))

