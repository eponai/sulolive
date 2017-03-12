(ns eponai.common.ui.product
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.common :as c]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.utils :as utils]
    #?(:cljs [eponai.web.utils :as web-utils])
    [om.next :as om :refer [defui]]
    [om.dom :as dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]))

(defn reviews-list [reviews]
  (apply dom/div
         #js {:className "user-reviews-container"}
         (map (fn [r]
                (dom/div #js {:className "user-review-item"}
                  (c/rating-element (:review/rating r))
                  (dom/p #js {:className "user-review-text"}
                         "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."

                         )))
              reviews)))

(def form-elements
  {:selected-sku "selected-sku"})

(defui Product
  static om/IQuery
  (query [_]
    [:db/id
     :store.item/name
     :store.item/price
     {:store.item/photos [:photo/path]}
     {:store.item/skus [:store.item.sku/value]}
     :store.item/details
     {:store.item/collection [:collection/label
                              :collection/id]}
     {:store/_items [{:store/photo [:photo/path]}
                     :store/name]}])
  Object
  (initLocalState [_]
    {:selected-tab :rating})

  (add-to-bag [this]
    #?(:cljs (let [selected-sku (web-utils/input-value-by-id (:selected-sku form-elements))]
               (debug "Selected sku value: " selected-sku)
               (om/transact! this `[(shopping-bag/add-item ~{:sku selected-sku})
                                    :query/cart])
               (om/update-state! this assoc :added-to-bag? true))))
  (componentDidUpdate [this prev-props prev-state]
    #?(:cljs (let [{:keys [added-to-bag?]} (om/get-state this)]
               (if added-to-bag?
                 (js/setTimeout #(om/update-state! this assoc :added-to-bag? false) 2000)))))
  (render [this]
    (let [{:keys [selected-tab added-to-bag?]} (om/get-state this)
          {:store.item/keys     [price photos details skus]
           item-name :store.item/name :as item} (om/props this)
          store (:store/_items item)
          photo-url (:photo/path (first photos))]

      ;(debug "Product props: " (om/props this))
      (dom/div
        #js {:id "sulo-product"}

        (my-dom/div
          (->> (css/grid-row)
               css/grid-column)
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column)
                   (css/grid-column-size {:small 12 :medium 8}))
              (photo/photo {:src photo-url})

              (my-dom/div
                (->> (css/grid-row)
                     (css/align :center))
                (my-dom/div (->> (css/grid-column)
                                 (css/grid-column-size {:small 8 :medium 6}))
                            (apply dom/div #js {:className "multi-photos-container"}
                                   (map-indexed
                                     (fn [i im]
                                          (dom/div #js {:key (str i)}
                                            (photo/thumbail
                                              {:src im})))
                                        (take 4 (repeat photo-url)))))))

            (my-dom/div
              (->> (css/grid-column)
                   (css/add-class ::css/product-info-container))
              (dom/div #js {:className "product-info"}
                (dom/p #js {:className "title"} item-name)
                (dom/p #js {:className "price"}
                        (utils/two-decimal-price price)))
              (when (not-empty skus)
                (dom/div nil
                  (dom/select #js {:id (get form-elements :selected-sku)}
                              (map-indexed
                                (fn [i sku]
                                  (dom/option #js {:key   (str i)
                                                   :value (:db/id sku)} (:store.item.sku/value sku)))
                                skus))))
              (dom/div #js {:className "product-action-container"}
                ;(my-dom/div (->> (css/grid-row))
                ;            (my-dom/div (->> (css/grid-column)
                ;                             (css/grid-column-size {:small 6 :medium 8}))
                ;                        (dom/a #js {:onClick   #(do #?(:cljs (.add-to-bag this item)))
                ;                                :className "button expanded"} "Add to bag"))
                ;            (my-dom/div (css/grid-column)
                ;                        (dom/a #js {:onClick   #(do #?(:cljs (.add-to-bag this item)))
                ;                                    :className "button expanded hollow"} "Save")))
                ;(dom/a #js {:onClick   #(do #?(:cljs (.add-to-bag this item)))
                ;            :className "button expanded hollow"} "Save")
                (dom/a #js {:onClick   #(.add-to-bag this)
                            :className "button expanded"} "Add to bag")
                (dom/p #js {:className (str (when added-to-bag? "show"))} "Your shopping bag was updated" ))))



          (my-dom/div (->> (css/grid-row) css/grid-column)
                      (dom/hr nil)
                      (my-dom/div
                        (->> (css/grid-row)
                             (css/align :middle)
                             (css/add-class :store-info))
                        (my-dom/div
                          (->> (css/grid-column)
                               (css/grid-column-size {:small 3 :medium 2}))
                          (photo/square
                            {:src (:photo/path (:store/photo store))}))

                        (my-dom/div
                          (css/grid-column)
                          (dom/div #js {:className "title"} (dom/span nil "Sold By"))
                          (dom/div #js {:className "store-name"}
                            (dom/a #js {:href (str "/store/" (:db/id store))}
                                   (dom/span nil (:store/name store))))
                          (dom/div #js {:className "store-tagline"} (dom/p nil "Some awesome tagline for the store"))
                          (dom/div nil (dom/a #js {:className "button hollow"} "+ Follow"))))
                      (dom/hr nil))

          (common/content-section {}
                                  (dom/div nil (dom/span nil "More from ") (dom/a nil (:store/name store)))
                                  (dom/div nil)
                                  "Load More")

          ;(my-dom/div
          ;  (css/grid-column)
          ;  (menu/horizontal
          ;    nil
          ;    (menu/item-tab {:active?  (= selected-tab :details)
          ;                    :on-click #(om/update-state! this assoc :selected-tab :details)}
          ;                   "Details")
          ;    (menu/item-tab {:active?  (= selected-tab :shipping)
          ;                    :on-click #(om/update-state! this assoc :selected-tab :shipping)}
          ;                   "Shipping")
          ;    (menu/item-tab {:active?  (= selected-tab :rating)
          ;                    :on-click #(om/update-state! this assoc :selected-tab :rating)}
          ;                   (c/rating-element 4 11)))
          ;  (cond (= selected-tab :rating)
          ;        (dom/div #js {:className "product-reviews"}
          ;          (reviews-list [{:review/rating 4}
          ;                         {:review/rating 3}
          ;                         {:review/rating 5}]))
          ;        (= selected-tab :details)
          ;        (dom/div #js {:className "product-details"}
          ;          details)
          ;
          ;        (= selected-tab :shipping)
          ;        (dom/div #js {:className "product-details"})))
          )))))

(def ->Product (om/factory Product))

(defui ProductPage
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/item (om/get-query Product)}])
  Object
  (render [this]
    (let [{:keys [query/item proxy/navbar]} (om/props this)]
      (common/page-container
        {:navbar navbar :id "sulo-product-page"}
        (->Product item)))))

(def ->ProductPage (om/factory ProductPage))