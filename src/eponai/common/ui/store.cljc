(ns eponai.common.ui.store
  (:require
    [eponai.common.ui.common :as common]
    #?(:cljs
       [eponai.web.ui.stream :as stream])
    [om.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]))

(defui Store
  static om/IQueryParams
  (params [_]
    #?(:cljs
       (let [path js/window.location.pathname]
         {:store-id (last (clojure.string/split path #"/"))})))
  static om/IQuery
  (query [_]
    ['({:query/store [:store/id
                     :store/cover
                     :store/photo
                     :store/goods
                     :store/name
                     :store/rating
                     :store/review-count]} {:store-id ?store-id})])
  Object
  (render [this]
    (let [{:keys [show-item]} (om/get-state this)
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

              (dom/ul #js {:className "menu vertical store-main-menu"}
                      (dom/li nil (dom/a nil "About"))
                      (dom/li nil (dom/a nil "Policies"))))

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
                        (common/product-element p {:on-click #(om/update-state! this assoc :show-item p)}))
                      goods)))

        (when (some? show-item)
          (common/modal {:on-close #(om/update-state! this dissoc :show-item)}
                        (dom/h1 nil "Show product: " (:item/name show-item))))))))

(def ->Store (om/factory Store))
