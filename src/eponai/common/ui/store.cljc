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
  static om/IQueryParams
  (params [_]
    #?(:cljs
       (let [path js/window.location.pathname]
         {:store-id (last (clojure.string/split path #"/"))
          :item (om/get-query item/Product) })
       :clj {:store-id nil}))
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/stream (om/get-query stream/Stream)}
     `({:query/store [:db/id
                      :store/cover
                      :store/photo
                      {:item/_store ~(om/get-query item/Product)}
                      {:stream/_store [:stream/name :stream/viewer-count]}
                      :store/name
                      :store/rating
                      :store/review-count]} {:store-id ~'?store-id})])
  Object
  (render [this]
    (let [{:keys [query/store proxy/navbar] :as props} (om/props this)
          {:keys      [store/cover store/review-count store/rating store/photo]
           stream     :stream/_store
           items      :item/_store
           store-name :store/name} store
          stream (first stream)]
      (common/page-container
        {:navbar navbar}
        (dom/div
          #js {:id "sulo-store-container"}
          (photo/cover
            (->> {:src cover}
                 css/grid-row)

            (my-dom/div
              (->> (css/grid-column)
                   (css/grid-column-size {:large 2})
                   (css/add-class :css/store-container))

              (dom/div #js {:className "store-short-info-container"}
                (photo/square
                  {:src photo})
                (dom/div #js {:className "content-item-title-section"}
                  (dom/h4 #js {:className "store-name"} store-name)
                  (common/rating-element rating review-count)))

              (menu/vertical
                {:classes [::css/store-main-menu]}
                (menu/item-link nil "About")
                (menu/item-link nil "Policies")))

            (my-dom/div
              (cond->> (->> (css/grid-column)
                            (css/grid-column-size {:small 12 :large 8}))
                       (some? stream)
                       (css/add-class :css/has-stream))
              (when (some? stream)
                (dom/div #js {:className "stream-container content-item"}
                  (stream/->Stream (:proxy/stream props))
                  (dom/div #js {:className "content-item-title-section"}
                    (dom/h5 #js {:className "stream-title"}
                            (:stream/name stream))
                    (dom/div #js {:className "viewers-container"}
                      (dom/i #js {:className "fa fa-eye fa-fw"})
                      (dom/h5 nil
                              (str (:stream/viewer-count stream))))))))

            (my-dom/div
              (->> (css/grid-column)
                   (css/grid-column-size {:medium 2})
                   (css/add-class ::css/stream-chat-container))
              (dom/div #js {:className "stream-chat-content"}
                (dom/span nil "This is a message"))
              (dom/div #js {:className "stream-chat-input"}
                (dom/input #js {:type        "text"
                                :placeholder "Your message..."})
                (dom/a #js {:className "button expanded"} "Send"))))

          (dom/div #js {:className "store-nav"}
            (my-dom/div
              (->> (css/grid-row)
                   (css/grid-column))
              (menu/horizontal
                nil
                (menu/item-link nil "Sheets")
                (menu/item-link nil "Pillows")
                (menu/item-link nil "Duvets"))))

          (my-dom/div
            nil
            (apply my-dom/div
              (->> (css/grid-row)
                   (css/grid-row-columns {:small 2 :medium 3 :large 4}))
                   (map (fn [p]
                          (pi/->ProductItem (om/computed {:product p}
                                                         {:display-content (item/->Product p)})))
                        items))))))))

(def ->Store (om/factory Store))
