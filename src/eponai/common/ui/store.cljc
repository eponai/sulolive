(ns eponai.common.ui.store
  (:require
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.product-item :as pi]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product :as item]
    [eponai.common.ui.stream :as stream]
    #?(:cljs [eponai.web.utils :as utils])
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]))

(defui Store
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/stream (om/get-query stream/Stream)}
     {:query/store [:db/id
                    :store/cover
                    :store/photo
                    {:item/_store (om/get-query item/Product)}
                    {:stream/_store [:stream/name :stream/viewer-count]}
                    :store/name
                    :store/rating
                    :store/review-count]}])
  Object
  (initLocalState [this]
    ;{:show-chat? false}
    )
  (componentWillReceiveProps [this next-props]
    (let [{:keys [query/store]} next-props
          stream (first (:stream/_store store))]
      (when (some? stream)
        (om/update-state! this assoc :show-chat? true))))
  (render [this]
    (let [st (om/get-state this)
          {:keys [query/store proxy/navbar] :as props} (om/props this)
          {:keys      [store/cover store/review-count store/rating store/photo]
           stream     :stream/_store
           items      :item/_store
           store-name :store/name} store
          stream (first stream)
          show-chat? (:show-chat? st (some? stream))]
      (dom/div #js {:id "sulo-store" :className "sulo-page"}
        (common/page-container
         {:navbar navbar}
         (dom/div
           #js {:id "sulo-store-container" :className (str (when show-chat? "chat-open"))}
           (photo/cover
             {:src (or cover "")}
             (my-dom/div
               (->> {:src cover}
                    ;css/grid-row
                    ;css/grid-column
                    )

               ;(my-dom/div
               ;  (->> (css/grid-column)
               ;       (css/grid-column-size {:large 2})
               ;       (css/add-class :css/store-container))
               ;
               ;  (dom/div #js {:className "store-short-info-container"}
               ;    (photo/square
               ;      {:src photo})
               ;    (dom/div #js {:className "content-item-title-section"}
               ;      (dom/h4 #js {:className "store-name"} store-name)
               ;      (common/rating-element rating review-count)))
               ;
               ;  (menu/vertical
               ;    {:classes [::css/store-main-menu]}
               ;    (menu/item-link nil "About")
               ;    (menu/item-link nil "Policies")))

               (my-dom/div
                 (cond->> (->> (css/grid-row) css/grid-column)
                          (some? stream)
                          (css/add-class :css/has-stream))
                 ;(when (some? stream))
                 (dom/div #js {:className "stream-container content-item"}
                   (stream/->Stream (:proxy/stream props))
                   (dom/div #js {:className "content-item-title-section"}
                     (dom/h5 #js {:className "stream-title"}
                             (:stream/name stream))
                     (dom/div #js {:className "viewers-container"}
                       (dom/i #js {:className "fa fa-eye fa-fw"})
                       (dom/span nil
                                 (str (:stream/viewer-count stream)))))))

               (my-dom/div
                 (cond->> (css/add-class ::css/stream-chat-container)
                          show-chat?
                          (css/add-class :show))
                 (my-dom/div
                   (->> {:onClick #(om/update-state! this update :show-chat? not)}
                        (css/add-class ::css/stream-toggle))
                   (my-dom/a (->> (css/add-class ::button) (css/add-class :expanded))
                             (if show-chat?
                               (dom/span nil ">>")
                               (dom/i #js {:className "fa fa-comments fa-fw"}))))
                 (dom/div #js {:className "stream-chat-content"}
                   (dom/span nil "This is a message"))
                 (dom/div #js {:className "stream-chat-input"}
                   (dom/input #js {:type        "text"
                                   :placeholder "Your message..."})
                   (dom/a #js {:className "button expanded"}
                          (dom/span nil "Send"))))))
           (my-dom/div nil
                       (my-dom/div
                         (->> (css/grid-row)
                              ;css/grid-column
                              ;(css/align :bottom)
                              (css/add-class :padded)
                              (css/add-class :vertical))
                         (my-dom/div
                           (->> (css/grid-column)
                                (css/grid-column-size {:small 2 :medium 2}))
                           (photo/square
                             {:src (:store/photo store)}))

                         (my-dom/div
                           (css/grid-column)
                           (dom/a #js {:href (str "/store/" (:db/id store))}
                                  (dom/p #js {:className "store-name"} (:store/name store)))
                           (common/rating-element (:store/rating store) (:store/review-count store)))))

           (dom/div #js {:className "store-nav"}
             (my-dom/div
               (->> (css/grid-row)
                    css/grid-column)
               (menu/horizontal
                 nil
                 (menu/item-link nil (dom/span nil "Sheets"))
                 (menu/item-link nil (dom/span nil "Pillows"))
                 (menu/item-link nil (dom/span nil "Duvets")))))

           (my-dom/div
             nil
             (apply my-dom/div
                    (->> (css/grid-row)
                         (css/grid-row-columns {:small 2 :medium 3}))
                    (map (fn [p]
                           (pi/->ProductItem (om/computed {:product p}
                                                          {:display-content (item/->Product p)})))
                         items))))
         {:class (str (when show-chat? "chat-open"))})))))

(def ->Store (om/factory Store))
