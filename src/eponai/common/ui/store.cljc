(ns eponai.common.ui.store
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product :as item]
    #?(:cljs
       [eponai.web.ui.stream :as stream])
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
    [`({:query/store [:store/id
                      :store/cover
                      :store/photo
                      {:store/goods ~(om/get-query item/Product)}
                      :store/name
                      :store/rating
                      :store/review-count]} {:store-id ~'?store-id})])
  Object
  (initLocalState [this]
    {:resize-listener #(.on-window-resize this)
     #?@(:cljs [:breakpoint (utils/breakpoint js/window.innerWidth)])})
  #?(:cljs
     (on-window-resize [this]
                       (om/update-state! this assoc :breakpoint (utils/breakpoint js/window.innerWidth))))
  (componentDidMount [this]
    #?(:cljs (.addEventListener js/window "resize" (:resize-listener (om/get-state this)))))
  (componentWillUnmount [this]
    #?(:cljs (.removeEventListener js/window "resize" (:resize-listener (om/get-state this)))))

  (render [this]
    (let [{:keys [show-item breakpoint]} (om/get-state this)
          {:keys [query/store]} (om/props this)
          {:keys      [store/cover store/review-count store/rating store/photo store/goods]
           store-name :store/name} store]

      (dom/div
        nil
        (dom/div #js {:className "cover-container"}
          (dom/div #js {:className "row collapse cover-photo" :style #js {:backgroundImage (str "url(" cover ")")}}

            (dom/div #js {:className "column store-container large-2"}

              (dom/div #js {:className "store-short-info-container"}
                (dom/div #js {:className "content-item-thumbnail-container"}
                  (dom/div #js {:className "content-item-thumbnail" :style #js {:backgroundImage (str "url(" photo ")")}}))

                (dom/div #js {:className "content-item-title-section"}
                  (dom/h1 #js {:className "store-name"} store-name)
                  (common/rating-element rating review-count)))

              #?(:cljs
                 (dom/ul #js {:className (str "menu store-main-menu" (when (utils/bp-compare :large breakpoint <) " vertical"))}
                         (dom/li nil (dom/a nil "About"))
                         (dom/li nil (dom/a nil "Policies")))))

            (dom/div #js {:className "large-8"}
              (dom/div #js {:id "stream-container"}
                #?(:cljs (stream/->Stream))))

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
          (apply dom/div #js {:className "content-items-container row small-up-2 medium-up-4"}
                 (map (fn [p]
                        (common/product-element p {:on-click #(om/update-state! this assoc :show-item p)
                                                   #?@(:cljs [:open-url? (utils/bp-compare :large breakpoint >)])}))
                      goods)))

        (when (some? show-item)
          (common/modal {:on-close #(om/update-state! this dissoc :show-item)
                         :size :large}
                        (item/->Product show-item)))))))

(def ->Store (om/factory Store))
