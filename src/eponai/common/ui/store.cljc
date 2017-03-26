(ns eponai.common.ui.store
  (:require
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.product-item :as pi]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.chat :as chat]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product :as item]
    [eponai.common.ui.stream :as stream]
    [eponai.client.routes :as routes]
    #?(:cljs [eponai.web.utils :as utils])
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]))

;(defn find-pos [el]
;  #?(:cljs (loop [cur-top 0
;                  obj el]
;             (if (.-offsetParent obj)
;               (recur (+ cur-top (.-offsetTop obj)) (.-offsetParent obj))
;               cur-top))
;     :clj  0))
(defui Store
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/stream (om/get-query stream/Stream)}
     {:proxy/chat (om/get-query chat/StreamChat)}
     {:query/store [:db/id
                    {:store/cover [:photo/path]}
                    {:store/photo [:photo/path]}
                    {:store/navigations [:store.navigation/label :store.navigation/path]}
                    ;{:store/items (om/get-query item/Product)}
                    {:stream/_store [:stream/name]}
                    :store/description
                    :store/name]}
     {:query/store-items (om/get-query item/Product)}
     :query/current-route])
  Object
  (render [this]
    (let [st (om/get-state this)
          {:query/keys [store store-items current-route]
           :proxy/keys [navbar] :as props} (om/props this)
          {:store/keys [cover photo]
           stream      :stream/_store
           ;items       :store/items
           store-name  :store/name} store
          stream (first stream)
          show-chat? (:show-chat? st (some? stream))
          has-stream? (some? stream)
          {:keys [route route-params]} current-route]
      ;(debug "Store items: " store-items)
      ;(debug "Store props: " (om/props this))
      (debug "Current route: " current-route)
      (dom/div #js {:id "sulo-store" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
          (my-dom/div
            nil
            (my-dom/div
              (->> (css/grid-row)
                   (css/grid-row-columns {:small 1})
                   (css/add-class :collapse)
                   (css/add-class :expanded))
              (my-dom/div
                (->> (css/grid-column)
                     (css/grid-column-order {:small 2 :medium 1}))
                (cond
                  (some? stream)
                  (my-dom/div
                    (cond->> (css/add-class :stream-container)
                             show-chat?
                             (css/add-class :sulo-show-chat))
                    (stream/->Stream (om/computed (:proxy/stream props)
                                                  {:stream-name (:stream/name stream)
                                                   :widescreen? true
                                                   :store store}))
                    (chat/->StreamChat (om/computed (:proxy/chat props)
                                                    {:on-toggle-chat (fn [show?]
                                                                       (om/update-state! this assoc :show-chat? show?))
                                                     :store store
                                                     :show? (some? stream)}))

                    ;(dom/div #js {:className "content-item-title-section"}
                    ;  (dom/span nil (:stream/name stream)))
                    )
                  (some? cover)
                  (photo/cover {:src (:photo/path cover)})))



              (my-dom/div
                (->> (css/grid-column)
                     (css/add-class :store-container)
                     (css/grid-column-order {:small 1 :medium 2}))
                (my-dom/div
                  (->> (css/grid-row)
                       (css/add-class :expanded)
                       (css/align :middle)
                       (css/align :center))
                  (my-dom/div
                    (->> (css/grid-column)
                         (css/grid-column-size {:small 12 :medium 2 :large 1}))
                    (photo/circle {:src (:photo/path photo)}))
                  (my-dom/div
                    (->> (css/grid-column)
                         (css/add-class :shrink))
                    (dom/div nil (dom/span nil (dom/strong #js {:className "store-name"} store-name)))
                    (dom/div nil (dom/p nil
                                        (dom/i #js {:className "fa fa-map-marker fa-fw"})
                                        (dom/strong nil (dom/small nil "North Vancouver, BC")))))
                  (my-dom/div
                    (->> (css/grid-column)
                         (css/add-class :follow-section)
                         (css/text-align :center)
                         (css/grid-column-size {:small 12 :medium 4 :large 3}))
                    (dom/div nil
                      (dom/a #js {:className "button"} "+ Follow")
                      (dom/a #js {:className "button hollow"} "Contact")))))
              (my-dom/div
                (->> (css/grid-column)
                     (css/add-class :quote-section)
                     (css/grid-column-order {:small 3 :medium 3}))
                (my-dom/div
                  (css/text-align :center)
                  (dom/span nil (:store/description store))))))

          (my-dom/div {:id "shop"}
                      (my-dom/div
                        (->> (css/grid-row)
                             (css/add-class :collapse)
                             (css/add-class :menu-container))
                        (my-dom/div
                          (css/grid-column)
                          (menu/horizontal
                            (css/align :center)

                            (menu/item (cond->> (css/add-class :about)
                                                (= route :store/about)
                                                (css/add-class ::css/is-active))
                              (dom/a #js {:href (routes/url :store/about {:store-id (:db/id store)})}
                                     (dom/span nil "About")))
                            (menu/item (when (= :store route)
                                         (css/add-class ::css/is-active))
                                       (dom/a #js {:href (routes/url :store {:store-id (:db/id store)})}
                                              (dom/span nil "All Items")))
                            (map-indexed
                              (fn [i n]
                                (let [{:store.navigation/keys [path label]} n
                                      is-active? (= path (:navigation route-params))]
                                  (menu/item
                                    (cond->> {:key (+ 10 i)}
                                             is-active?
                                             (css/add-class ::css/is-active))
                                    (dom/a #js {:href (routes/url :store/navigation {:navigation path
                                                                                              :store-id (:db/id store)})}
                                                    (dom/span nil label)))))
                              (:store/navigations store)))))

                      (apply my-dom/div
                             (->> (css/grid-row)
                                  (css/grid-row-columns {:small 2 :medium 3}))
                             (map-indexed
                               (fn [i p]
                                    (my-dom/div
                                      (css/grid-column {:key i})
                                      (pi/->ProductItem {:product p})))
                                  (concat store-items store-items store-items)))))))))

(def ->Store (om/factory Store))
