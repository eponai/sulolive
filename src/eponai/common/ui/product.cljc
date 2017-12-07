(ns eponai.common.ui.product
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.utils :as utils]
    #?(:cljs [eponai.web.utils :as web-utils])
    [om.next :as om :refer [defui]]
    [eponai.common.ui.common :as common]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.format :as f]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as photo]
    [eponai.web.social :as social]
    [eponai.common.photos :as photos]
    [eponai.client.routes :as routes]
    [eponai.common.mixpanel :as mixpanel]
    [clojure.string :as string]
    [cemerick.url :as url]
    [eponai.common.analytics.google :as ga]))

(defn product-url [product]
  (if-let [product-id (:db/id product)]
    (routes/url :product {:product-id   product-id
                          :product-name (-> (:store.item/name product "")
                                            (subs 0 (min (count (:store.item/name product)) 40))
                                            (string/replace #" " "-"))})
    (error "Trying to create URL for product with no :product-id, doing nothing. Make sure product has a :db/id")))

(defn product-schema-markup [product & [{:keys [description availability]}]]
  (let [{:store.item/keys [photos skus ]
         store            :store/_items} product
        out-of-stock? (every? #(= :out-of-stock (-> % :store.item.sku/inventory :store.item.sku.inventory/value)) skus)
        description-text (when description (clojure.string/replace description #"<(?:.|\n)*?>" ""))]
    (dom/div {:itemType  "https://schema.org/Product"
              :itemScope true}
             (dom/meta-tag {:itemProp "name" :content (:store.item/name product)})
             (dom/meta-tag {:itemProp "brand" :content (-> store :store/profile :store.profile/name)})
             (dom/meta-tag {:itemProp "description" :content description-text})
             (dom/meta-tag {:itemProp "image" :content (photos/transform (:photo/id (:store.item.photo/photo (first photos))) :transformation/thumbnail-large)})
             (dom/meta-tag {:itemProp "category" :content (-> product :store.item/category :category/label)})
             (dom/meta-tag {:itemProp "url" :content (str "https://sulo.live" (product-url product))})
             (dom/div {:itemType  "https://schema.org/Offer"
                       :itemProp  "offers"
                       :itemScope true}
                      (dom/meta-tag {:itemProp "availability" :itemType "https://schema.org/ItemAvailability" :content (str availability)})
                      (dom/meta-tag {:itemProp "price" :content (:store.item/price product)})
                      (dom/meta-tag {:itemProp "priceCurrency" :content "CAD"})))))

(defn product-query []
  [:db/id

   :store.item/name
   :store.item/price
   :store.item/index
   :store.item/not-found?
   {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                        :store.item.photo/index]}
   {:store.item/skus [:db/id :store.item.sku/variation :store.item.sku/inventory]}
   :store.item/description
   :store.item/section
   {:store.item/category [:category/label
                          :category/path
                          :category/name]}
   {:store/_items [{:store/profile [{:store.profile/photo [:photo/path :photo/id]}
                                    :store.profile/name
                                    :store.profile/tagline]}
                   {:store/owners [{:store.owner/user [:user/online?]}]}
                   {:stream/_store [:stream/state]}
                   {:store/locality [:sulo-locality/path]}
                   {:store/shipping [{:shipping/rules [{:shipping.rule/destinations [:country/code]}]}]}
                   {:store/status [:status/type]}
                   :store/username]}])

(defn get-pos [element]
  #?(:cljs
     (loop [el element
            lx 0 ly 0]
       (if (some? el)
         (recur (.-offsetParent el) (+ lx (.-offsetLeft el)) (+ ly (.-offsetTop el)))
         {:x lx :y ly}))))
(defn on-zoom [img-dimensions event index]
  #?(:cljs
     (let [^js/Event e event
           img (web-utils/element-by-id (str "sulo-product-photo-" index "-container"))
           background (web-utils/element-by-id (str "sulo-product-photo-" index "-background"))
           img-width (.-offsetWidth img)
           img-height (.-offsetHeight img)
           {:keys [x y]} (get-pos img)
           rel-x (/ (- (.-clientX e) x) img-width)
           rel-y (/ (- (.-clientY e) y) img-height)

           ;; The background position is in coordinates with origin in the top left corner.
           background-x (- (* rel-x img-width) (* rel-x (:width img-dimensions)))
           background-y (- (* rel-y img-height) (* rel-y (:height img-dimensions)))]
       (set! (.-backgroundSize (.-style background)) (str (:width img-dimensions) "px " (:height img-dimensions) "px"))
       (set! (.-backgroundPosition (.-style background)) (str background-x "px " background-y "px")))))

(defn zoom-out [event index]
  #?(:cljs
     (let [background (web-utils/element-by-id (str "sulo-product-photo-" index "-background"))]
       (set! (.-backgroundSize (.-style background)) "contain")
       (set! (.-backgroundPosition (.-style background)) "center center"))))

(def form-elements
  {:selected-sku "selected-sku"})



(defui Product
  Object
  (initLocalState [_]
    {:selected-tab       :rating
     :active-photo-index 0})

  (select-photo [this index]
    #?(:cljs
       (let [{:store.item/keys [photos]} (om/props this)
             {item-photo :store.item.photo/photo} (get (into [] (sort-by :store.item.photo/index photos)) (or index 0))
             img (new js/Image.)
             img-url (photos/transform (:photo/id item-photo) :transformation/preview)]
         (debug "IMG URL: " img-url)
         (set! (.-onload img) #(do
                                (debug "img dod load: " {:width  (.-width img)
                                                         :height (.-height img)})
                                (om/update-state! this assoc :img-dimensions {:width  (.-width img)
                                                                              :height (.-height img)})))
         (set! (.-src img) img-url)

         (om/update-state! this assoc :active-photo-index index))))
  (add-to-bag [this]
    #?(:cljs (let [{:keys [store.item/skus] :as product} (om/props this)
                   variations (filter #(some? (:store.item.sku/variation %)) skus)
                   selected-sku (if (not-empty variations)
                                  (web-utils/input-value-or-nil-by-id (:selected-sku form-elements))
                                  (:db/id (first (:store.item/skus product))))]
               (debug "Selected sku value: " selected-sku)
               (when (some? selected-sku)
                 (mixpanel/track "Add product to bag")
                 (ga/send-add-to-bag product (some #(when (= (:db/id %) selected-sku) %) skus))
                 (om/transact! this `[(shopping-bag/add-items ~{:skus [selected-sku]})
                                      {:query/cart [{:user.cart/items [:db/id]}
                                                    {:user/_cart [:db/id]}]}])
                 (om/update-state! this assoc :added-to-bag? true)))))
  (componentDidMount [this]
    (let [product (om/props this)]
      (ga/send-product-detail-view product)
      (.select-photo this 0)))
  (componentDidUpdate [this prev-props prev-state]
    #?(:cljs (let [{:keys [added-to-bag?]} (om/get-state this)]
               (if added-to-bag?
                 (js/setTimeout #(om/update-state! this assoc :added-to-bag? false) 2000)))))
  (render [this]
    (let [{:keys [selected-tab added-to-bag? active-photo-index img-dimensions]} (om/get-state this)
          {:keys [on-leave]} (om/get-computed this)
          {:store.item/keys [price photos details skus]
           item-name        :store.item/name :as item} (om/props this)
          store (:store/_items item)
          photo-url (photos/transform (:photo/id (:store.item.photo/photo (first photos))) :transformation/thumbnail-large)
          variations (filter #(some? (:store.item.sku/variation %)) skus)
          out-of-stock? (every? #(= :out-of-stock (-> % :store.item.sku/inventory :store.item.sku.inventory/value)) skus)
          description-html (f/bytes->str (:store.item/description item))]
      (debug "Store: " store)
      (dom/div
        {:id "sulo-product"}

        (product-schema-markup item {:description description-html
                                     :availability (if out-of-stock? "OutOfStock" "InStock")})

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
                  (map-indexed
                    (fn [i p]
                      (menu/item
                        (cond->> (css/add-class :orbit-slide {:key (+ 10 i)})
                                 (= active-photo-index i)
                                 (css/add-class ::css/is-active)
                                 (= active-photo-index i)
                                 (css/add-class ::css/is-in))
                        (photo/product-photo
                          item
                          (->> {:id (str "sulo-product-photo-" i) :index i
                                ;:onMouseMove #(on-zoom img-dimensions % i)
                                ;:onMouseOut #(zoom-out % i)
                                }
                               (css/add-class :orbit-image)
                               (css/add-class :contain)))))
                    photos))
                (dom/nav
                  (css/add-class :orbit-bullets)
                  (map-indexed
                    (fn [i p]
                      (dom/button
                        {:onClick #(.select-photo this i)}
                        (photo/product-thumbnail item {:index          i
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
                           {:id           (get form-elements :selected-sku)
                            :defaultValue (or (first (filter #(not= :out-of-stock (-> % :store.item.sku/inventory
                                                                                      :store.item.sku.inventory/value)) variations))
                                              (-> variations first :db/id))}
                           (map
                             (fn [sku]
                               (debug "SKU: " sku)
                               (let [is-disabled (= :out-of-stock (-> sku :store.item.sku/inventory :store.item.sku.inventory/value))]
                                 (dom/option
                                   (cond-> {:value (:db/id sku)}
                                           is-disabled
                                           (assoc :disabled true))
                                   (str (:store.item.sku/variation sku) (when is-disabled " - out of stock")))))
                             variations))))
              (dom/div
                (css/add-class :product-action-container)

                (dom/a
                  (cond->> (->> {:onClick #(when-not out-of-stock?
                                            (.add-to-bag this))}
                                (css/button)
                                (css/expanded))
                           out-of-stock?
                           (css/add-class :disabled))
                  (dom/span nil "Add to bag"))
                (dom/p
                  (when (or added-to-bag? out-of-stock?)
                    (css/add-class :show))
                  (dom/small
                    (cond added-to-bag?
                          (css/add-class :text-success)
                          out-of-stock?
                          (css/add-class :text-alert))

                    (if out-of-stock?
                      "Out of stock"
                      "Your shopping bag was updated")))

                (let [#?@(:cljs [item-url (str js/window.location.origin (product-url item))]
                          :clj  [item-url nil])]
                  (debug "Share url: " item-url)
                  (menu/horizontal
                    (->> (css/align :right)
                         (css/add-class :share-menu))
                    (menu/item
                      nil
                      (social/share-button {:on-click #(mixpanel/track "Share on social media" {:platform "facebook"
                                                                                                :object   "product"})
                                            :platform :social/facebook
                                            :href     item-url}))
                    (menu/item
                      nil
                      (social/share-button {:on-click    #(mixpanel/track "Share on social media" {:platform "twitter"
                                                                                                   :object   "product"})
                                            :platform    :social/twitter
                                            :description item-name
                                            :href        item-url}))
                    (menu/item
                      nil
                      (social/share-button {:on-click    #(mixpanel/track "Share on social media" {:platform "pinterest"
                                                                                                   :object   "product"})
                                            :platform    :social/pinterest
                                            :href        item-url
                                            :description item-name
                                            :media       (photos/transform (get-in (first photos) [:store.item.photo/photo :photo/id])
                                                                           :transformation/preview)})))))))

          (grid/row-column
            (css/add-class :store-info)
            (dom/hr nil)
            (grid/row
              (css/align :middle)
              (grid/column
                (grid/column-size {:small 2 :medium 1})
                (photo/store-photo store {:transformation :transformation/thumbnail}))

              (grid/column
                (grid/column-size {:small 7 :medium 9})
                (dom/div
                  (css/add-class :title) (dom/span nil "Sold By"))
                (dom/div
                  (css/add-class :store-name)
                  (dom/a
                    {:href (routes/store-url store :store)
                     :onClick on-leave}
                    (dom/span nil (:store.profile/name (:store/profile store)))))
                (dom/div
                  (css/add-class :store-tagline)
                  (dom/p nil (:store.profile/tagline (:store/profile store)))))
              (grid/column
                (->> (css/text-align :right)
                     (grid/column-size {:small 3 :medium 2}))
                (dom/div nil
                         (common/follow-button nil))))
            (dom/hr nil))

          (grid/row-column
            (css/add-class :product-details)
            (dom/p nil (dom/strong nil "Product details"))
            (quill/->QuillRenderer {:html description-html})))))))

(def ->Product (om/factory Product))
