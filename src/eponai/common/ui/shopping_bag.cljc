(ns eponai.common.ui.shopping-bag
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.router :as router]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.icons :as icons]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.button :as button]
    [eponai.common.mixpanel :as mixpanel]
    [eponai.common.ui.product :as product]
    [eponai.web.ui.content-item :as ci]))

(defn items-by-store [items]
  (group-by #(get-in % [:store.item/_skus :store/_items]) items))

(defn compute-item-price [items]
  (reduce + (map :store.item/price items)))

(defn store-element [s]
  (let [store-name (get-in s [:store/profile :store.profile/name])]
    (grid/row
      (->> (css/add-class :store-container)
           (css/align :center))

      (grid/column
        (grid/column-size {:small 3 :medium 2 :large 1})
        (photo/store-photo s {:transformation :transformation/thumbnail}))

      (grid/column
        (->> (grid/column-size {:small 12})
             (css/text-align :center))
        (dom/p nil
               (dom/strong
                 (css/add-class :store-name) store-name))))))

(defn sku-menu-item [component sku]
  (let [item (get sku :store.item/_skus)
        {:store.item/keys [price photos]
         product-id       :db/id
         item-name        :store.item/name :as product} item
        {:store.item.photo/keys [photo]} (first (sort-by :store.item.photo/index photos))]
    (menu/item
      (css/add-class :sl-productlist-item--row)
      (grid/row
        (->> (css/align :middle)
             (css/add-class :item)
             (css/add-class :sl-productlist-item--cell))
        (grid/column
          (grid/column-size {:small 2 :medium 1})
          (photo/product-preview product {:transformation :transformation/thumbnail}))

        (grid/column
          (grid/column-size {:small 8})

          (dom/div nil
                   (dom/a
                     (->> {:href (product/product-url item)}
                          (css/add-class :name))
                     (dom/span nil item-name)))
          (dom/div nil
                   (dom/span (css/add-class :variation) (:store.item.sku/variation sku))))



        ;(grid/column
        ;  (css/text-align :right)
        ;  (dom/i {:classes ["fa fa-trash-o"]}))
        (grid/column
          (->>
            (css/add-class :shrink)
            (css/align :right)
            (css/add-class :sl-productlist-item--cell))
          (button/default-hollow
            {:onClick #(.remove-item component sku)}
            (dom/span nil "Remove"))
          ;(dom/input {:type             "number"
          ;               ;; :defaultValue doesn't work for clj dom/input.
          ;               ;; File om.next/dom bug?
          ;                :defaultValue 1
          ;                #?@(:clj [:value 1])})
          )
        (grid/column
          (->> (css/text-align :right)
               (css/add-class :sl-productlist-item--cell))
          (dom/div nil
                   (dom/span
                     (css/add-class :price)
                     (utils/two-decimal-price price))))

        ))))

(defn store-items-element [component skus-by-store]
  (dom/div
    nil
    (map
      (fn [[s skus]]
        (let [store-is-open? (= :status.type/open (-> s :store/status :status/type))]
          (when store-is-open?
            (dom/div
              (->> (css/callout)
                   (css/add-class :cart-checkout-item))
              (store-element s)
              (menu/vertical
                nil
                (map #(sku-menu-item component %) skus))
              (grid/row
                (->> (css/align :middle)
                     (css/text-align :right)
                     (css/add-class :item))
                (let [item-price (compute-item-price (map #(get % :store.item/_skus) skus))
                      shipping-price 0]
                  (grid/column
                    nil
                    (dom/p (css/add-class :total-price)
                           (dom/span nil "Total: ")
                           (dom/strong nil (utils/two-decimal-price (+ item-price shipping-price))))
                    (button/button-cta
                      {:href    nil                         ;(routes/url :checkout {:store-id (:db/id s)})
                       :onClick #(mixpanel/track "Checkout shopping bag" {:store-id   (:db/id s)
                                                                          :store-name (get-in s [:store/profile :store.profile/name])
                                                                          :item-count (count skus)})}
                      (dom/span nil "Checkout")))))))))
      skus-by-store)))

(defui ShoppingBag
  static om/IQuery
  (query [_]
    [{:query/cart [{:user/_cart [:db/id]}
                   {:user.cart/items [:store.item.sku/variation
                                      :db/id
                                      :store.item.sku/inventory
                                      {:store.item/_skus [:store.item/price
                                                          {:store.item/photos [{:store.item.photo/photo [:photo/id]}
                                                                               :store.item.photo/index]}
                                                          :store.item/name
                                                          {:store/_items [:db/id
                                                                          {:store/status [:status/type]}
                                                                          {:store/profile [:store.profile/name
                                                                                           {:store.profile/photo [:photo/id]}]}]}]}]}]}
     {:query/featured-items (om/get-query ci/ProductItem)}
     :query/locations
     {:query/auth [:user/email]}
     :query/current-route])
  Object
  (remove-item [this sku]
    (debug "SKU REMOVE: " sku)
    (om/transact! this [(list 'shopping-bag/remove-item
                              {:sku (:db/id sku)})
                        :query/cart]))
  (componentWillReceiveProps [this p]
    (let [{:keys [did-mount?]} (om/get-state this)]
      (if-not did-mount?
        (om/update-state! this assoc :did-mount? true))))
  (render [this]
    (let [{:query/keys [current-route cart locations featured-items]} (om/props this)
          {:keys [user.cart/items]} cart
          store-is-open? (fn [s] (= :status.type/open (-> s :store/status :status/type)))
          skus-by-store (filter #(store-is-open? (key %)) (items-by-store items))]
      (debug "Shopping bag: " cart)
      (debug "Items by store: " (items-by-store items))
      (debug "SKUS by store: " skus-by-store)
      (dom/div
        {:id "sulo-shopping-bag"}

        (grid/row-column
          nil
          (dom/h1 nil "Shopping bag")
          (if (not-empty skus-by-store)
            (dom/div nil
                     (store-items-element this skus-by-store))

            [(dom/div
               (->> (css/text-align :center)
                    (css/add-class :cart-empty))
               (dom/div
                 (css/add-class :empty-container)
                 (dom/p (css/add-class :shoutout) "Your shopping bag is empty"))
               (icons/empty-shopping-bag)
               ;(dom/p (css/add-class :header))
               (button/button
                 (button/sulo-dark (button/hollow {:href (routes/url :browse/all-items {:locality (:sulo-locality/path locations)})}))
                 (dom/span nil "Go to the market - start shopping")))
             (grid/row-column
                  nil
                  (dom/hr nil)
                  (dom/div
                    (css/add-class :section-title)
                    (dom/h3 nil (str "New arrivals in " (:sulo-locality/title locations)))))
             (grid/row
               (->>
                 (grid/columns-in-row {:small 2 :medium 3 :large 5}))
               (map
                 (fn [p]
                   (grid/column
                     (css/add-class :new-arrival-item)
                     (ci/->ProductItem (om/computed p
                                                    {:current-route current-route
                                                     :open-url? true}))))
                 (take 5 featured-items)))]))))))

(def ->ShoppingBag (om/factory ShoppingBag))

(router/register-component :shopping-bag ShoppingBag)