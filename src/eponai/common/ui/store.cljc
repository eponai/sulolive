(ns eponai.common.ui.store
  (:require
    [eponai.common.ui.common :as common]
    [om.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]))

(defui Store
  static om/IQuery
  (query [_]
    [{:query/store [:store/id
                    :store/cover
                    :store/photo
                    :store/goods
                    :store/name
                    :store/rating
                    :store/review-count]}])
  Object
  (render [this]
    (let [{:keys [query/store]} (om/props this)
          {:keys      [store/cover store/review-count store/rating store/photo store/goods]
           store-name :store/name} store]
      (dom/div
        nil
        (dom/div {:className "cover-container"}
          (dom/div {:className "row collapse cover-photo" :style {:background-image (str "url(" cover ")")}}

            (dom/div {:className "column store-container large-2"}

              (dom/div {:className "store-short-info-container"}
                (dom/div {:className "content-item-thumbnail-container"}
                  (dom/div {:className "content-item-thumbnail" :style {:background-image (str "url(" photo ")")}}))

                (dom/div {:className "content-item-title-section"}
                  (dom/h1 {:className "store-name"} store-name)
                  (common/rating-element rating review-count)))

              (dom/ul {:className "menu vertical store-main-menu"}
                      (dom/li nil (dom/a nil "About"))
                      (dom/li nil (dom/a nil "Policies"))))

            (dom/div {:className "large-8"}
              (dom/div {:id "stream-container"}))

            (dom/div {:className "medium-2 stream-chat-container"}
              (dom/div {:className "stream-chat-content"}
                (dom/span nil "This is a message"))
              (dom/div {:className "stream-chat-input"}
                (dom/input {:type        "text"
                            :placeholder "Your message..."})
                (dom/a {:className "button expanded"} "Send")))))

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
                      (shuffle goods))))))))

(def ->Store (om/factory Store))
