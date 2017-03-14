(ns eponai.common.ui.streams
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.product-item :as pi]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]))

(defui Streams
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/streams [:stream/name {:stream/store [:store/name {:store/photo [:photo/path]}]}]}])
  Object
  (render [this]
    (let [{:keys [query/streams proxy/navbar]} (om/props this)]
      (common/page-container
        {:navbar navbar :id "sulo-live" :class-name "sulo-browse"}
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (css/grid-column)
            (dom/h1 nil (.toUpperCase "Live"))))
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column)
                 (css/add-class :navigation)
                 (css/grid-column-size {:large 3})
                 (css/show-for {:size :large}))
            (menu/vertical
              nil
              (menu/item-link nil "Live now")
              (menu/item-link nil "Scheduled streams")
              (menu/item-link nil "New arrivals")
              (menu/item-link nil "Popular")))
          (my-dom/div
            (css/grid-column)
            (dom/div #js {:id "sulo-items-container"}
              (dom/strong nil "LIVE NOW")
              (apply dom/div #js {:className "row small-up-2 medium-up-3"}
                     (map (fn [s]
                            (common/online-channel-element s))
                          streams)))))))))

(def ->Streams (om/factory Streams))
