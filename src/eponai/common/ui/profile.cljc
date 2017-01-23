(ns eponai.common.ui.profile
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]))

(defui Profile
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}])
  Object
  (initLocalState [_]
    {:tab :following})
  (render [this]
    (let [{:keys [query/items proxy/navbar]} (om/props this)
          {:keys [tab]} (om/get-state this)]
      (dom/div
        #js {:id "sulo-profile" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
          (dom/div #js {:className "header"}
            (my-dom/div (->> (css/grid-row)
                             (css/align :center))
                        (my-dom/div (->> (css/grid-column)
                                         (css/grid-column-size {:small 2}))
                                    (photo/square
                                      {:src "https://scontent.fsjc1-3.fna.fbcdn.net/v/t1.0-9/10155053_10154618777965285_5052794971256899825_n.jpg?oh=f9cfc7ead53db03f9f8b1e1f6cbe75a5&oe=591823F9"})))
            (my-dom/div (->> (css/grid-row)
                             (css/align :center))
                        (my-dom/div (->> (css/grid-column)
                                         (css/grid-column-size {:small 2})
                                         (css/text-align :center))
                                    (dom/a #js {:className "button gray hollow"} (dom/span nil "+ Follow"))))
            (my-dom/div (->> (css/grid-row)

                             css/grid-column)
                        (menu/horizontal
                          (css/align :center)
                          (menu/item-tab {:active?  (= tab :following)
                                          :on-click #(om/update-state! this assoc :tab :following)} "Following")
                          (menu/item-tab {:active?  (= tab :followers)
                                          :on-click #(om/update-state! this assoc :tab :followers)} "Followers")
                          (menu/item-tab {:active?  (= tab :products)
                                          :on-click #(om/update-state! this assoc :tab :products)} "Products")
                          (menu/item-tab {:active?  (= tab :about)
                                          :on-click #(om/update-state! this assoc :tab :about)} "About")))))))))

(def ->Profile (om/factory Profile))
