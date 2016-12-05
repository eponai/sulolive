(ns eponai.server.ui.store
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.server.parser.read :as read]
    [eponai.server.ui.common :as common]))

(defui Store
  static om/IQuery
  (query [_]
    [{:query/store [:store/id
                    :store/cover
                    :store/photo
                    :store/goods
                    :store/name]}])
  Object
  (render [this]
    (let [{:keys [release? query/store]} (om/props this)
          {:keys [store/photo store/cover store/goods]
           store-name :store/name } store]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (common/head release?))

        (dom/body
          nil
          (dom/div
            {:id "sulo-store"
             :className "page-container"}
            (common/navbar nil)
            (dom/div {:className "page-content"}
              (dom/div {:className "cover-container"}
                (dom/div {:className "cover-photo" :style {:background-image (str "url(" cover ")")}}

                  (dom/div {:className "store-container cover-photo-section large-2"}
                    (dom/div {:className "row store-short-info-container"}
                      (dom/div {:className "column small-2 large-12"}
                        (dom/div {:className "content-item-thumbnail-container"}
                          (dom/div {:className "content-item-thumbnail" :style {:background-image (str "url(" photo ")")}})))

                      (dom/div {:className "content-item-title-section"}
                        (dom/h1 {:className "store-name"} store-name)
                        (dom/div {:className "user-rating-container"}
                          (dom/i {:className "fa fa-star fa-fw"})
                          (dom/i {:className "fa fa-star fa-fw"})
                          (dom/i {:className "fa fa-star fa-fw"})
                          (dom/i {:className "fa fa-star fa-fw"})
                          (dom/i {:className "fa fa-star fa-fw"})
                          ;(dom/img {:className "user-rating"
                          ;          :src       "/assets/img/rating-5.png"})
                          (dom/small nil "(23)")))))

                  (dom/div {:className "cover-photo-section grow large-8"}
                    (dom/div {:id "stream-container" }))

                  (dom/div {:id        "stream-chat"
                            :className "small-shrink cover-photo-section medium-2"}
                    (dom/span nil "This is a message"))
                  ))

              (dom/div {:className "store-nav"}
                (dom/div {:className "row column"}
                  (dom/ul {:className "menu"}
                          (dom/li nil (dom/a nil "Sheets"))
                          (dom/li nil (dom/a nil "Pillows"))
                          (dom/li nil (dom/a nil "Duvets")))))

              (dom/div {:className "items"}
                (apply dom/div {:className "content-items-container row small-up-2 medium-up-4"}
                       (map (fn [p]
                              (common/product-element p))
                            (shuffle (apply concat (take 4 (repeat goods))))))))
            (common/footer nil))

          ;(dom/script {:src "https://webrtc.github.io/adapter/adapter-latest.js"})
          (dom/script {:src "/lib/videojs/video.min.js"})
          (dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
          (dom/script {:src "/lib/videojs/videojs.hls.min.js"})
          (dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})

          (common/inline-javascript ["env.web.main.runstream()"]))))))
