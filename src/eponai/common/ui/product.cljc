(ns eponai.common.ui.product
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.common :as c]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.utils :as utils]
    #?(:cljs [eponai.web.utils :as web-utils])
    [om.next :as om :refer [defui]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.router :as router]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.format :as f]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as p]))

;(defn reviews-list [reviews]
;  (apply dom/div
;         #js {:className "user-reviews-container"}
;         (map (fn [r]
;                (dom/div #js {:className "user-review-item"}
;                  (c/rating-element (:review/rating r))
;                  (dom/p #js {:className "user-review-text"}
;                         "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
;
;                         )))
;              reviews)))

(def form-elements
  {:selected-sku "selected-sku"})

(defui Product
  static om/IQuery
  (query [_]
    [:db/id
     :store.item/name
     :store.item/price
     {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                          :store.item.photo/index]}
     {:store.item/skus [:db/id :store.item.sku/variation :store.item.sku/inventory]}
     :store.item/description
     :store.item/section
     {:store.item/category [:category/label
                            :category/path
                            :category/name]}
     {:store/_items [{:store/profile [{:store.profile/photo [:photo/path :photo/id]}
                                      :store.profile/name]}]}])
  Object
  (initLocalState [_]
    {:selected-tab       :rating
     :active-photo-index 0})

  (add-to-bag [this]
    #?(:cljs (let [{:keys [store.item/skus] :as product} (om/props this)
                   variations (filter #(some? (:store.item.sku/variation %)) skus)
                   selected-sku (if (not-empty variations)
                                  (web-utils/input-value-or-nil-by-id (:selected-sku form-elements))
                                  (:db/id (first (:store.item/skus product))))]
               (debug "Selected sku value: " selected-sku)
               (when (some? selected-sku)
                 (om/transact! this `[(shopping-bag/add-item ~{:sku selected-sku})
                                      :query/cart])
                 (om/update-state! this assoc :added-to-bag? true)))))
  (componentDidUpdate [this prev-props prev-state]
    #?(:cljs (let [{:keys [added-to-bag?]} (om/get-state this)]
               (if added-to-bag?
                 (js/setTimeout #(om/update-state! this assoc :added-to-bag? false) 2000)))))
  (render [this]
    (let [{:keys [selected-tab added-to-bag? active-photo-index]} (om/get-state this)
          {:store.item/keys [price photos details skus]
           item-name        :store.item/name :as item} (om/props this)
          store (:store/_items item)
          photo-url (:photo/path (first photos))
          variations (filter #(some? (:store.item.sku/variation %)) skus)]
      (dom/div
        {:id "sulo-product"}

        (grid/row-column
          nil
          (grid/row
            nil
            (grid/column
              (grid/column-size {:small 12 :medium 8})
              (dom/div
                (css/add-class :orbit)
                (menu/horizontal
                  (css/add-class :orbit-container)
                  ;(dom/button #js {:className "orbit-next"
                  ;                 :onClick   #(when (> (dec (count photos)) active-photo-index)
                  ;                              (om/update-state! this update :active-photo-index inc))}
                  ;            (dom/i #js {:className "fa fa-caret-right fa-2x"}))
                  ;(dom/button #js {:className "orbit-previous"
                  ;                 :onClick #(when (< 0 active-photo-index)
                  ;                            (om/update-state! this update :active-photo-index dec))}
                  ;            (dom/i #js {:className "fa fa-caret-left fa-2x"}))
                  (map-indexed
                    (fn [i p]
                      (menu/item
                        (cond->> (css/add-class :orbit-slide {:key (+ 10 i)})
                                 (= active-photo-index i)
                                 (css/add-class ::css/is-active)
                                 (= active-photo-index i)
                                 (css/add-class ::css/is-in))
                        (p/product-photo
                          item
                          (->> {:index i}
                               (css/add-class :orbit-image)
                               (css/add-class :contain)))))
                    photos))
                (dom/nav
                  (css/add-class :orbit-bullets)
                  (map-indexed
                    (fn [i p]
                      (dom/button
                        {:onClick #(om/update-state! this assoc :active-photo-index i)}
                        (p/product-thumbnail item {:index          i
                                                   :transformation :transformation/thumbnail})))
                    photos))))

            (grid/column
              (css/add-class ::css/product-info-container)
              (dom/div
                (css/add-class :product-info)
                (dom/p (css/add-class :title) item-name)
                (dom/p (css/add-class :price)
                       (utils/two-decimal-price price)))
              (when (not-empty variations)
                (dom/div nil
                         (dom/select
                           {:id (get form-elements :selected-sku)}
                           (map
                             (fn [sku]
                               (dom/option
                                 {:value (:db/id sku)} (:store.item.sku/variation sku)))
                             variations))))
              (dom/div
                (css/add-class :product-action-container)
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
                (dom/a
                  (cond->> (->> {:onClick #(when (not-empty skus)
                                            (.add-to-bag this))}
                                (css/button)
                                (css/expanded))
                           (empty? skus)
                           (css/add-class :disabled)) "Add to bag")
                (dom/p
                  (cond->> (css/add-class :text-success)
                           added-to-bag?
                           (css/add-class :show)) "Your shopping bag was updated")
                (dom/p
                  (cond->> (css/add-class :text-alert)
                           (empty? skus)
                           (css/add-class :show)) "Out of stock"))

              (dom/div
                nil
                (quill/->QuillRenderer {:html (f/bytes->str (:store.item/description item))}))))



          (grid/row-column
            nil
            (dom/hr nil)
            (grid/row
              (->> (css/align :middle)
                   (css/add-class :store-info))
              (grid/column
                (grid/column-size {:small 3 :medium 2})
                (p/store-photo store {:transformation :transformation/thumbnail})
                )

              (grid/column
                nil
                (dom/div
                  (css/add-class :title) (dom/span nil "Sold By"))
                (dom/div
                  (css/add-class :store-name)
                  (dom/a
                    {:href (str "/store/" (:db/id store))}
                    (dom/span nil (:store.profile/name (:store/profile store)))))
                (dom/div
                  (css/add-class :store-tagline) (dom/p nil (:store.profile/description (:store/profile store))))
                (dom/div nil
                         (common/follow-button nil))))
            (dom/hr nil))

          ;(common/content-section {}
          ;                        (dom/div nil (dom/span nil "More from ") (dom/a nil (:store.profile/name (:store/profile store))))
          ;                        (dom/div nil)
          ;                        "Load More")

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
