(ns eponai.common.ui.checkout
  (:require
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.utils :as utils]
    ;[eponai.client.routes :as routes]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]))

(defn store-element [s]
  (let [{:store/keys [photo] store-name :store/name} s]
    (my-dom/div
      (->> (css/grid-row)
           (css/add-class :expanded)
           (css/add-class :store-container)
           (css/align :center)
           )
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 3 :medium 2 :large 1}))
        (photo/circle {:src (:photo/path photo)}))
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 12})
             (css/text-align :center))
        (dom/div nil (dom/p nil (dom/strong #js {:className "store-name"} store-name)))))))

(defn store-checkout-element [component store cart-items]
  (dom/div #js {:className "callout transparent cart-checkout-item"}
    (store-element store)
    ;(my-dom/div
    ;  (css/grid-row))

    (map (fn [sku]
           (let [{:store.item/keys [price photos]
                  product-id       :db/id
                  item-name        :store.item/name} (get sku :store.item/_skus)]
             (my-dom/div
               (->> (css/grid-row)
                    (css/add-class :collapse)
                    (css/align :middle)
                    (css/add-class :callout)
                    (css/add-class :transparent)
                    (css/add-class :item))

               (my-dom/div
                 (->> (css/grid-column)
                      (css/grid-column-size {:small 3 :medium 2 :large 1}))
                 (photo/square
                   {:src (:photo/path (first photos))}))

               ;(my-dom/div
               ;  (->> (css/grid-column))
               ;  (dom/a #js {:className "close-button"} (dom/small nil "x")))
               (my-dom/div
                 (->> (css/grid-column)
                      (css/grid-column-size {:small 8}))

                 (dom/div #js {:className ""}
                   (dom/a #js {
                               ;:href      (routes/url :product {:product-id product-id})
                               :className "name"}
                          (dom/span nil item-name)))
                 (dom/div #js {:className ""}
                   (dom/span nil (:store.item.sku/value sku))))

               (my-dom/div
                 (->> (css/grid-column)
                      (css/align :right)
                      (css/grid-column-size {:small 3 :medium 2 :large 1})
                      (css/grid-column-offset {:small 3 :large 0}))
                 (dom/input #js {:type         "number"
                                 :defaultValue 1}))
               (my-dom/div
                 (->> (css/grid-column)
                      (css/text-align :right)
                      )
                 (dom/div #js {:className ""}
                   (dom/span #js {:className "price"}
                             (utils/two-decimal-price price)))))))
         cart-items)))

(defui Checkout
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [{:cart/items [:db/id
                                 :store.item.sku/uuid
                                 :store.item.sku/value
                                 {:store.item/_skus [:store.item/price
                                                     {:store.item/photos [:photo/path]}
                                                     :store.item/name
                                                     {:store/_items [:db/id
                                                                     :store/name
                                                                     {:store/photo [:photo/path]}]}]}]}]}
     :query/current-route
     {:query/auth [:user/email]}])
  Object
  (render [this]
    (let [{:query/keys [current-route cart auth]
           :proxy/keys [navbar]} (om/props this)
          {:keys [route route-params]} current-route
          {:keys [store-id]} route-params

          checkout-items (filter #(= store-id (get-in % [:store.item/_skus :store/_items :db/id])))]
      (debug "Items; " checkout-items)
      (debug "Checkout props: " (om/props this))
      (common/page-container
        {:navbar navbar :id "sulo-checkout"}
        (my-dom/div
          (->> (css/grid-row)
               (css/add-class :collapse))
          (my-dom/div (css/grid-column)
                      (store-checkout-element this nil (:cart/items cart)))

          )
        (my-dom/div
          (->> (css/grid-row)
               (css/add-class :collapse))
          (my-dom/div
            (css/grid-column)
            (dom/div #js {:className "callout transparent"}
              (dom/label nil "Email")
              (dom/input #js {:type     "text"
                              :value    (:user/email auth)
                              :disabled true})))

          )

        (my-dom/div
          (->> (css/grid-row)
               (css/add-class :collapse))
          (my-dom/div
            (css/grid-column)
            (dom/div #js {:className "callout transparent"}
              (dom/h4 nil "Payment")
              (my-dom/div
                (css/grid-row)
                (my-dom/div
                  (css/grid-column)
                  (dom/label nil (dom/span nil "Card number"))
                  (dom/input #js {:type "text"})))

              (my-dom/div
                (css/grid-row)
                (my-dom/div
                  (css/grid-column)
                  (dom/label nil (dom/span nil "Expiry month"))
                  (dom/select nil
                              (map (fn [i]
                                     (dom/option #js {:value i} (str (inc i))))
                                   (range 12))))
                (my-dom/div
                  (css/grid-column)
                  (dom/label nil (dom/span nil "Expiry year"))
                  (dom/select nil
                              (map (fn [i]
                                     (dom/option #js {:value i} (str (inc i))))
                                   (range 12)))))

              (my-dom/div
                (css/grid-row)
                (my-dom/div
                  (css/grid-column)
                  (dom/label nil "Postal code")
                  (dom/input #js {:type "text"}))
                (my-dom/div
                  (css/grid-column)
                  (dom/label nil (dom/span nil "CVC"))
                  (dom/input #js {:type "number"
                                  :placeholder "e.g. 123"}))))))
        ;(dom/div #js {:className "callout transparent"})
        (dom/div nil "checkout your stuff perhaps.")))))

(def ->Checkout (om/factory Checkout))