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

(defn find-pos [el]
  #?(:cljs (loop [cur-top 0
                  obj el]
             (if (.-offsetParent obj)
               (recur (+ cur-top (.-offsetTop obj)) (.-offsetParent obj))
               cur-top))
     :clj  0))
(defui Store
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/stream (om/get-query stream/Stream)}
     {:query/store [:db/id
                    {:store/cover [:photo/path]}
                    {:store/photo [:photo/path]}
                    {:store/items (om/get-query item/Product)}
                    {:stream/_store [:stream/name]}
                    :store/name]}])
  Object
  (componentWillReceiveProps [this next-props]
    (let [{:keys [query/store]} next-props
          stream (first (:stream/_store store))]
      (when (some? stream)
        (om/update-state! this assoc :show-chat? true))))
  (render [this]
    (let [st (om/get-state this)
          {:keys [query/store proxy/navbar] :as props} (om/props this)
          {:store/keys [cover photo]
           stream      :stream/_store
           items       :store/items
           store-name  :store/name} store
          stream (first stream)
          show-chat? (:show-chat? st (some? stream))
          has-stream? (some? stream)]
      (debug "Store items: " items)
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
                (when (or (some? stream) cover)
                  (dom/div #js {:className "stream-container"}
                    (stream/->Stream (:proxy/stream props))
                    (dom/div #js {:className "content-item-title-section"}
                      (dom/span nil (:stream/name stream))))))



              (my-dom/div
                (->> (css/grid-column)
                     (css/grid-column-order {:small 1 :medium 2}))
                (my-dom/div
                  (->> (css/grid-row)
                       (css/add-class :expanded)
                       (css/align :middle)
                       (css/align :center))
                  (my-dom/div
                    (->> (css/grid-column)
                         (css/grid-column-size {:small 4 :medium 2 :large 1}))
                    (photo/circle {:src (get-in store [:store/photo :photo/path])}))
                  (my-dom/div
                    (->> (css/grid-column)
                         (css/add-class :shrink))
                    (dom/div nil (dom/span nil (dom/strong #js {:className "store-name"} store-name)))
                    (dom/div nil (dom/p nil
                                        (dom/i #js {:className "fa fa-map-marker fa-fw"})
                                        (dom/strong nil (dom/small nil "North Vancouver, BC"))))
                    (dom/div nil
                      (dom/p nil
                             (dom/a #js {:className "button"} "+ Follow")
                             (dom/a #js {:className "button hollow"} "Contact")))))
                (my-dom/div
                  (->> (css/grid-row)
                       css/grid-column
                       (css/add-class :expanded)
                       (css/text-align :center))
                  (dom/span nil "Keep calm and wear pretty stuff"))))

            ;(my-dom/div
            ;  nil
            ;  (my-dom/div
            ;    (->> (css/grid-row)
            ;         (css/add-class :collapse)
            ;         (css/add-class :store-container))
            ;    (my-dom/div
            ;      (->> (css/grid-column)
            ;           (css/grid-column-size {:small 12 :medium 3})
            ;           (css/add-class :store-info)
            ;           (css/text-align :center))
            ;      (photo/circle
            ;        {:src (:photo/path (:store/photo store))})
            ;

            ;      (dom/div nil (dom/a #js {:className "button expanded hollow"}
            ;                          (dom/span nil "+ Follow")))
            ;      (when-not has-stream?
            ;        (dom/div nil (dom/a #js {:className "button expanded hollow"}
            ;                            (dom/i #js {:className "fa fa-comment-o fa-fw"})
            ;                            (dom/span nil "Contact")))))
            ;    (my-dom/div
            ;      (->> (css/grid-column))
            ;      (photo/cover
            ;        {:src (if cover
            ;                (:photo/path cover)
            ;                "")})))
            ;  (my-dom/div
            ;    (->> (css/grid-row)
            ;         (css/align :center))
            ;    (my-dom/div (->> (css/grid-column)
            ;                     (css/grid-column-size {:small 12})
            ;                     (css/text-align :center))
            ;                (dom/span nil "Keep calm and wear pretty jewelry"))))


            )
          ;(my-dom/div
          ;  nil
          ;  (my-dom/div (->> (css/grid-row))
          ;              (my-dom/div (css/grid-column)
          ;                          (dom/a #js {:className "button hollow"} (dom/span nil "+ Follow")))
          ;              (my-dom/div (css/grid-column)
          ;                          (dom/a #js {:className "button hollow"} (dom/span nil "About")))
          ;              (my-dom/div (css/grid-column)
          ;                          (dom/a #js {:className "button hollow"} (dom/span nil "Contact")))))

          (my-dom/div {:id "shop"}
                      (my-dom/div
                        (->> (css/grid-row)
                             (css/add-class :collapse)
                             (css/add-class :menu-container))
                        (my-dom/div
                          (css/grid-column)
                          (menu/horizontal
                            (css/align :center)
                            (menu/item-link {:classes [:about]} (dom/span nil "About"))
                            (menu/item-link nil (dom/span nil "Sheets"))
                            (menu/item-link nil (dom/span nil "Pillows"))
                            (menu/item-link nil (dom/span nil "Duvets")))))

                      (apply my-dom/div
                             (->> (css/grid-row)
                                  (css/grid-row-columns {:small 2 :medium 3}))
                             (map (fn [p]
                                    (pi/->ProductItem (om/computed {:product p}
                                                                   {:display-content (item/->Product p)})))
                                  items))))))))

(def ->Store (om/factory Store))
