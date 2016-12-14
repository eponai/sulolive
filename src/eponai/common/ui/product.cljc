(ns eponai.common.ui.product
  (:require
    [eponai.common.ui.common :as c]
    [eponai.common.ui.utils :as utils]
    [om.next :as om :refer [defui]]
    [om.dom :as dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]))

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

(defui Product
  static om/IQuery
  (query [_]
    [:db/id
     :item/name
     :item/price
     :item/img-src
     :item/details
     :item/category
     {:item/store [:store/photo
                   :store/name
                   :store/rating
                   :store/review-count]}])
  Object
  (initLocalState [_]
    {:selected-tab :details})
  (render [this]
    (let [{:keys [selected-tab]} (om/get-state this)
          {:keys     [item/price item/store item/img-src item/details]
           item-name :item/name :as item} (om/props this)]
      (debug "Render product: " item)
      (dom/div
        #js {:id "sulo-product"}

        (dom/div #js {:className "row content-items-container store-container align-middle"}
          (dom/div #js {:className "columns small-2 medium-1"}
            (photo/square (:store/photo store) ))
          (dom/div #js {:className "columns"}
            (dom/a #js {:href (str "/store/" (:db/id store))}
                   (dom/p #js {:className "store-name"} (:store/name store)))
            (c/rating-element (:store/rating store) (:store/review-count store))))


        (dom/div #js {:className "row column product-container"}
          (dom/div #js {:className "row"}
            (dom/div #js {:className "column small-12 medium-8 small-order-2 medium-order-1"}
              (photo/photo img-src)

              (apply dom/div #js {:className "multi-photos-container"}
                     (map (fn [im]
                            (photo/thumbail im))
                          (take 4 (repeat img-src)))))
            (dom/div #js {:className "column product-info-container small-order-1 medium-order-2"}
              (dom/div #js {:className "product-info"}
                (dom/h1 #js {:className "product-info-title"} item-name)
                (dom/h2 #js {:className "product-info-price"}
                        (utils/two-decimal-price price)))
              (dom/div #js {:className "product-action-container clearfix"}
                (dom/a #js {:onClick   #(om/transact! this `[(shopping-bag/add-item ~{:item (select-keys item [:db/id])})
                                                             :query/cart])
                            :className "button expanded"} "Add to bag"))))


          (dom/div #js {:className "row column product-details-container"}
            (dom/ul #js {:className "product-details-menu menu"}
                    (dom/li
                      #js {:className (when (= selected-tab :details) "active")}
                      (dom/a #js {:onClick #(om/update-state! this assoc :selected-tab :details)}
                             "Details"))
                    (dom/li #js {:className (when (= selected-tab :shipping) "active")}
                            (dom/a #js {:onClick #(om/update-state! this assoc :selected-tab :shipping)}
                                   "Shipping"))
                    (dom/li #js {:className (when (= selected-tab :rating) "active")}
                            (dom/a #js {:onClick #(om/update-state! this assoc :selected-tab :rating)} (c/rating-element 4 11))))

            (cond (= selected-tab :rating)
                  (dom/div #js {:className "product-reviews"}
                    (reviews-list [{:review/rating 4}
                                   {:review/rating 3}
                                   {:review/rating 5}]))
                  (= selected-tab :details)
                  (dom/div #js {:className "product-details"}
                    details)

                  (= selected-tab :shipping)
                  (dom/div #js {:className "product-details"}))))))))

(def ->Product (om/factory Product))
;`({:query/store [:db/id
;                 :store/cover
;                 :store/photo
;                 {:item/_store ~(om/get-query item/Product)}
;                 {:stream/_store [:stream/name :stream/viewer-count]}
;                 :store/name
;                 :store/rating
;                 :store/review-count]} {:store-id ~'?store-id})

(defui ProductPage
  static om/IQueryParams
  (params [_]
    #?(:cljs
       (let [path js/window.location.pathname]
         {:product-id (last (clojure.string/split path #"/"))})))
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     `({:query/item ~(om/get-query Product)} {:product-id ~'?product-id})])
  Object
  (render [this]
    (let [{:keys [query/item proxy/navbar]} (om/props this)]
      (common/page-container
        {:navbar navbar}
        (->Product item)))))

(def ->ProductPage (om/factory ProductPage))