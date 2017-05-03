(ns eponai.common.ui.shopping-bag
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.icons :as icons]))

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
        (photo/store-photo s :transformation/thumbnail))

      (grid/column
        (->> (grid/column-size {:small 12})
             (css/text-align :center))
        (dom/p nil
               (dom/strong
                 (css/add-class :store-name) store-name))))))

(defn sku-menu-item [sku]
  (let [{:store.item/keys [price photos]
         product-id       :db/id
         item-name        :store.item/name} (get sku :store.item/_skus)
        {:store.item.photo/keys [photo]} (first (sort-by :store.item.photo/index photos))]
    (menu/item
      nil
      (grid/row
        (->> (css/align :middle)
             (css/add-class :item))
        (grid/column
          (grid/column-size {:small 3 :medium 2 :large 1})
          (photo/product-photo photo))

        (grid/column
          (grid/column-size {:small 8})

          (dom/div nil
                   (dom/a
                     (->> {:href      (routes/url :product {:product-id product-id})}
                   (css/add-class :name))
                     (dom/span nil item-name)))
          (dom/div nil
                   (dom/span (css/add-class :variation) (:store.item.sku/variation sku))))

        (grid/column
          (->> (grid/column-size {:small 3 :medium 2 :large 1})
               (grid/column-offset {:small 3 :large 0})
               (css/align :right))
          (dom/input {:type             "number"
                         ;; :defaultValue doesn't work for clj dom/input.
                         ;; File om.next/dom bug?
                          :defaultValue 1
                          #?@(:clj [:value 1])}))

        (grid/column
          (css/text-align :right)
          (dom/div nil
                   (dom/span
              (css/add-class :price)
              (utils/two-decimal-price price))))))))

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
            (map sku-menu-item skus))
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
                       (css/button)) "Checkout"))))))
      skus-by-store)))

(defui ShoppingBag
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [{:user.cart/items [:db/id
                                 :store.item.sku/variation
                                 {:store.item/_skus [:store.item/price
                                                     {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                                                          :store.item.photo/index]}
                                                     :store.item/name
                                                     {:store/_items [{:store/profile [:store.profile/name
                                                                                      {:store.profile/photo [:photo/path]}]}]}]}]}]}
     {:query/auth [:user/email]}])
  Object
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
              (dom/a
                (->> {:href (routes/url :browse/all-items)}
                     (css/button-hollow))
                (dom/span nil "Go to the market - start shopping")))))))))

(def ->ShoppingBag (om/factory ShoppingBag))