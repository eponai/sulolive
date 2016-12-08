(ns eponai.common.ui.store
  (:require
    [eponai.common.ui.product-item :as pi]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product :as item]
    [eponai.common.ui.stream :as stream]
    #?(:cljs [eponai.web.utils :as utils])
    [om.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]))

(defui Store
  static om/IQueryParams
  (params [_]
    #?(:cljs
       (let [path js/window.location.pathname]
         {:store-id (last (clojure.string/split path #"/"))
          :item (om/get-query item/Product) })))
  static om/IQuery
  (query [_]
    [`({:query/store [:db/id
                      :store/cover
                      :store/photo
                      {:item/_store ~(om/get-query item/Product)}
                      {:stream/_store [:stream/name :stream/viewer-count]}
                      :store/name
                      :store/rating
                      :store/review-count]} {:store-id ~'?store-id})])
  Object
  ;(initLocalState [this]
  ;  {:resize-listener #(.on-window-resize this)
  ;   #?@(:cljs [:breakpoint (utils/breakpoint js/window.innerWidth)])})
  ;#?(:cljs
  ;   (on-window-resize [this]
  ;                     (om/update-state! this assoc :breakpoint (utils/breakpoint js/window.innerWidth))))
  ;(componentDidMount [this]
  ;  #?(:cljs (.addEventListener js/window "resize" (:resize-listener (om/get-state this)))))
  ;(componentWillUnmount [this]
  ;  #?(:cljs (.removeEventListener js/window "resize" (:resize-listener (om/get-state this)))))

  (render [this]
    (let [{:keys [show-item breakpoint]} (om/get-state this)
          {:keys [query/store]} (om/props this)
          {:keys      [store/cover store/review-count store/rating store/photo]
           stream     :stream/_store
           items      :item/_store
           store-name :store/name} store]

      (dom/div
        nil
        (dom/div #js {:className "cover-container"}
          (dom/div #js {:className "photo cover" :style #js {:backgroundImage (str "url(" cover ")")}}

            (dom/div #js {:className "column store-container large-2"}

              (dom/div #js {:className "store-short-info-container"}
                (dom/div #js {:className "photo-container"}
                  (dom/div #js {:className "photo square" :style #js {:backgroundImage (str "url(" photo ")")}}))

                (dom/div #js {:className "content-item-title-section"}
                  (dom/h1 #js {:className "store-name"} store-name)
                  (common/rating-element rating review-count)))

              ;#?(:cljs)
              (dom/ul #js {:className (str "menu store-main-menu")}
                      (dom/li nil (dom/a nil "About"))
                      (dom/li nil (dom/a nil "Policies"))))

            (dom/div #js {:className "large-8"}
              (dom/div #js {:className "stream-container content-item"}
                (stream/->Stream)
                (dom/div #js {:className "content-item-title-section"}
                  (dom/h2 #js {:className "stream-title"} (:stream/name stream))
                  (common/viewer-element (:stream/viewer-count stream)))))

            (dom/div #js {:className "medium-2 stream-chat-container"}
              (dom/div #js {:className "stream-chat-content"}
                (dom/span nil "This is a message"))
              (dom/div #js {:className "stream-chat-input"}
                (dom/input #js {:type        "text"
                            :placeholder "Your message..."})
                (dom/a #js {:className "button expanded"} "Send")))))

        (dom/div #js {:className "store-nav"}
          (dom/div #js {:className "row column"}
            (dom/ul #js {:className "menu"}
                    (dom/li nil (dom/a nil "Sheets"))
                    (dom/li nil (dom/a nil "Pillows"))
                    (dom/li nil (dom/a nil "Duvets")))))

        (dom/div #js {:className "items"}
          (apply dom/div #js {:className "content-items-container row small-up-2 medium-up-3 large-up-4"}
                 (map (fn [p]
                        (pi/->ProductItem {:product p
                                               :on-click #(om/update-state! this assoc :show-item p)}))
                      items)))

        ;(when (some? show-item)
        ;  (common/modal {:on-close #(om/update-state! this dissoc :show-item)
        ;                 :size :large}
        ;                (item/->Product show-item)))
        ))))

(def ->Store (om/factory Store))
