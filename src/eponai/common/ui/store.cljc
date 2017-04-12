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
    [eponai.common.ui.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.grid :as grid]))

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
                    {:stream/_store [:stream/title]}
                    :store/description
                    :store/name]}
     {:query/store-items (om/get-query item/Product)}
     :query/current-route])
  Object
  (render [this]
    (let [{:keys [fullscreen?] :as st} (om/get-state this)
          {:query/keys [store store-items current-route]
           :proxy/keys [navbar] :as props} (om/props this)
          {:store/keys [cover photo]
           stream      :stream/_store
           store-name  :store/name} store
          stream (first stream)
          show-chat? (:show-chat? st (some? stream))
          {:keys [route route-params]} current-route]
      (common/page-container
        {:navbar navbar
         :id     "sulo-store"}
        (dom/div
          nil
          (grid/row
            (->> (grid/columns-in-row {:small 1})
                 (css/add-class :collapse)
                 (css/add-class :expanded))
            (grid/column
              (grid/column-order {:small 2 :medium 1})
              (cond
                (some? stream)
                (dom/div
                  (cond->> (css/add-class :stream-container)
                           show-chat?
                           (css/add-class :sulo-show-chat)
                           fullscreen?
                           (css/add-class :fullscreen))
                  (stream/->Stream (om/computed (:proxy/stream props)
                                                {:stream-title (:stream/title stream)
                                                 :widescreen? true
                                                 :store       store
                                                 :on-fullscreen-change #(om/update-state! this assoc :fullscreen? %)}))
                  (chat/->StreamChat (om/computed (:proxy/chat props)
                                                  {:on-toggle-chat (fn [show?]
                                                                     (om/update-state! this assoc :show-chat? show?))
                                                   :store          store
                                                   :show?          (some? stream)})))
                (some? cover)
                (photo/cover {:src (:photo/path cover)})))



            (grid/column
              (->> (grid/column-order {:small 1 :medium 2})
                   (css/add-class :store-container))

              (grid/row
                (->> (css/align :middle)
                     (css/align :center))

                (grid/column
                  (grid/column-size {:small 12 :medium 2})
                  (photo/store-photo store))

                (grid/column
                  (css/add-class :shrink)
                  (dom/div (css/add-class :store-name) (dom/strong nil store-name))
                  (dom/p nil
                         ;(dom/i
                         ;     (css/add-class "fa fa-map-marker fa-fw"))
                         (dom/small nil "North Vancouver, BC")))
                (grid/column
                  (->> (grid/column-size {:small 12 :medium 4 :large 3})
                       (css/text-align :center)
                       (css/add-class :follow-section))
                  (dom/div nil
                           (dom/a (css/button) "+ Follow")
                           (dom/a (css/button-hollow) "Contact")))))
            (grid/column
              (->> (grid/column-order {:small 3 :medium 3})
                   (css/add-class :quote-section)
                   (css/text-align :center))
              (dom/span nil (:store/description store)))))

        (dom/div
          {:id "shop"}
          (grid/row
            (->> (css/add-class :collapse)
                 (css/add-class :menu-container))
            (grid/column
              nil
              (menu/horizontal
                (css/align :center)

                (menu/item (cond->> (css/add-class :about)
                                    (= route :store/about)
                                    (css/add-class ::css/is-active))
                           (dom/a {:href (routes/url :store/about {:store-id (:db/id store)})}
                                  (dom/span nil "About")))
                (menu/item (when (= :store route)
                             (css/add-class ::css/is-active))
                           (dom/a {:href (routes/url :store {:store-id (:db/id store)})}
                                  (dom/span nil "All Items")))
                (map-indexed
                  (fn [i n]
                    (let [{:store.navigation/keys [path label]} n
                          is-active? (= path (:navigation route-params))]
                      (menu/item
                        (cond->> {:key (+ 10 i)}
                                 is-active?
                                 (css/add-class ::css/is-active))
                        (dom/a
                          {:href (routes/url :store/navigation {:navigation path :store-id   (:db/id store)})}
                          (dom/span nil label)))))
                  (:store/navigations store)))))

          (grid/products (concat store-items store-items store-items)
                         (fn [p]
                           (pi/->ProductItem {:product p}))))))))

(def ->Store (om/factory Store))
