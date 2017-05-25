(ns eponai.common.ui.shopping-bag
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.router :as router]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.icons :as icons]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.button :as button]))

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
  (let [{:store.item/keys [price photos]
         product-id       :db/id
         item-name        :store.item/name :as product} (get sku :store.item/_skus)
        {:store.item.photo/keys [photo]} (first (sort-by :store.item.photo/index photos))]
    (menu/item
      nil
      (grid/row
        (->> (css/align :middle)
             (css/add-class :item))
        (grid/column
          (grid/column-size {:small 3 :medium 2 :large 1})
          (photo/product-preview product {:transformation :transformation/thumbnail}))

        (grid/column
          (grid/column-size {:small 8})

          (dom/div nil
                   (dom/a
                     (->> {:href (routes/url :product {:product-id product-id})}
                          (css/add-class :name))
                     (dom/span nil item-name)))
          (dom/div nil
                   (dom/span (css/add-class :variation) (:store.item.sku/variation sku))))



        ;(grid/column
        ;  (css/text-align :right)
        ;  (dom/i {:classes ["fa fa-trash-o"]}))
        (grid/column
          (css/text-align :right)
          (dom/div nil
                   (dom/span
                     (css/add-class :price)
                     (utils/two-decimal-price price))))
        (grid/column
          (->>
            (css/add-class :shrink)
            (css/align :right))
          (dom/a {:onClick #(.remove-item component sku)} (dom/i {:classes ["fa fa-trash-o"]}))
          ;(dom/input {:type             "number"
          ;               ;; :defaultValue doesn't work for clj dom/input.
          ;               ;; File om.next/dom bug?
          ;                :defaultValue 1
          ;                #?@(:clj [:value 1])})
          )
        ))))

(defn store-items-element [component skus-by-store]
  (dom/div
    nil
    (map
      (fn [[s skus]]
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
                (dom/p nil
                       (dom/span nil "Total: ")
                       (dom/strong nil (utils/two-decimal-price (+ item-price shipping-price))))
                (dom/a
                  (->> {:href (routes/url :checkout {:store-id (:db/id s)})}
                       (css/button)) "Checkout"))))
          ))
      skus-by-store)))

(defui ShoppingBag
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [{:user.cart/items [:store.item.sku/variation
                                      :db/id
                                      :store.item.sku/inventory
                                      {:store.item/_skus [:store.item/price
                                                          {:store.item/photos [{:store.item.photo/photo [:photo/id]}
                                                                               :store.item.photo/index]}
                                                          :store.item/name
                                                          {:store/_items [{:store/profile [:store.profile/name
                                                                                           {:store.profile/photo [:photo/id]}]}]}]}]}]}
     {:query/auth [:user/email]}])
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
    (let [{:keys [query/cart proxy/navbar]} (om/props this)
          {:keys [user.cart/items]} cart
          skus-by-store (items-by-store items)]
      (debug "Shopping bag: " cart)
      (common/page-container
        {:navbar navbar :id "sulo-shopping-bag"}

        (grid/row-column
          nil
          (dom/h1 nil "Shopping bag")
          (callout/callout-small
            (css/add-class :warning)
            (dom/small nil "Purchases are disabled until we finish work on the payment integration. Hang tight, we're almost there!"))
          (if (not-empty items)
            (dom/div nil
                     (store-items-element this skus-by-store))
            (dom/div
              (->> (css/callout)
                   (css/text-align :center)
                   (css/add-class :cart-empty))
              (dom/p nil (dom/strong nil "Your shopping bag is empty"))
              (icons/empty-shopping-bag)
              ;(dom/p (css/add-class :header))
              (button/button
                (button/hollow {:href (routes/url :browse/all-items)})
                (dom/span nil "Go to the market - start shopping")))))))))

(def ->ShoppingBag (om/factory ShoppingBag))

(router/register-component :shopping-bag ShoppingBag)