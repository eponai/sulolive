(ns eponai.common.ui.profile
  (:require
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]
    [taoensso.timbre :refer [debug]]))

(defui Profile
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}])
  Object
  (initLocalState [_]
    {:tab :following
     :file-upload? false
     :photo-url "https://s3.amazonaws.com/sulo-images/site/collection-women.jpg"})
  (render [this]
    (let [{:keys [query/items proxy/navbar]} (om/props this)
          {:keys [tab file-upload? photo-url]} (om/get-state this)]
      (dom/div
        #js {:id "sulo-profile" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
          (dom/div #js {:className "header"}
            #?(:cljs
               (when file-upload?
                 (common/modal {:on-close #(om/update-state! this assoc :file-upload? false)}
                               (my-dom/div (css/grid-row)
                                           (pu/->PhotoUploader {:on-change #(let [new-url (get-in (first %) [:response :location])]
                                                                             (om/update-state! this assoc :file-upload? false :photo-url new-url))})))))
            (my-dom/div (->> (css/grid-row)
                             (css/align :center))
                        (my-dom/div (->> (css/grid-column)
                                         (css/grid-column-size {:small 2}))
                                    (dom/a #js {:onClick #(om/update-state! this assoc :file-upload? true)}
                                           (photo/circle
                                             {:src photo-url}))))
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
