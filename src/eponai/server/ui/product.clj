(ns eponai.server.ui.product
  (:require
    [eponai.server.ui.common :as common]
    [eponai.server.ui.store :as store]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Product
  Object
  (render [this]
    (let [{:keys [release? params]} (om/props this)
          {:keys [product-id]} params
          goods (mapcat :goods store/stores)
          product (some #(when (= product-id (:id %)) %) goods)
          store (some #(when (some #{product-id} (mapv :id (:goods %)))
                        %) store/stores)]
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
                    (dom/div {:className "content-item-thumbnail" :style {:background-image (str "url(" (:photo store) ")")}})))
                (dom/div {:className "columns"}
                  (dom/a {:href (str "/store/" (:id store))} (dom/h1 {:className "store-name"} (:name store)))
                  (dom/div {:className "user-rating-container"}
                    (dom/img {:className "user-rating"
                              :src       "/assets/img/rating-5.png"})
                    (dom/small nil "(23)"))))


              (dom/div {:className "row product-container"}
                (dom/div {:className "column small-12 medium-8"}
                  (dom/div {:className "product-photo-container"}
                    (dom/div {:className "product-photo" :style {:background-image (str "url(" (:img-src product) ")")}})))
                (dom/div {:className "column product-info-container"}
                  (dom/div {:className "product-info"}
                    (dom/h2 {:className "product-info-title"} (:name product))
                    (dom/h3 {:className "product-info-price"}
                            (:price product)))
                  (dom/div {:className "product-action-container clearfix"}
                    (dom/a {:className "button expanded"} "Add to Cart"))))
              ;(dom/div {:className "items"}
              ;         (apply dom/div {:className "content-items-container row small-up-2 medium-up-4"}
              ;                (map (fn [p]
              ;                       (common/product-element p))
              ;                     (shuffle goods))))
              )

            (common/footer nil))

          ;(dom/script {:src "https://webrtc.github.io/adapter/adapter-latest.js"})
          (dom/script {:src "/lib/videojs/video.min.js"})
          (dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
          (dom/script {:src "/lib/videojs/videojs.hls.min.js"})
          (dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})

          (common/inline-javascript ["env.web.main.runstream()"]))))))

