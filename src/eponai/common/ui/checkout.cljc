(ns eponai.common.ui.checkout
  (:require
    [om.dom :as dom]
    [eponai.common.ui.dom :as my-dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]))

(defn items-by-store [items]
  (group-by :item/store items))

(defn compute-item-price [items]
  (reduce + (map :item/price items)))

(defn store-element [s]
  (my-dom/div
    (->> (css/grid-row)
         (css/align :middle))

    (my-dom/div
      (->> (css/grid-column)
           (css/grid-column-size {:small 2 :medium 1}))
      (photo/square
        {:src (:store/photo s)}))

    (my-dom/div
      (->> (css/grid-column))
      (dom/a #js {:href (str "/store/" (:db/id s))}
             (dom/p #js {:className "store-name"} (:store/name s)))
      (common/rating-element (:store/rating s) (:store/review-count s)))))

(defn store-checkout-element [s items]
  (dom/div #js {:className "callout cart-checkout-item"}
    (store-element s)

    (my-dom/div
      (->> (css/grid-row)
           (css/align :bottom))
      (apply my-dom/div
             (->> (css/grid-column)
                  (css/grid-column-size {:small 2 :medium 8}))
             (map (fn [i]
                    (my-dom/div
                      (->> (css/grid-row)
                           (css/align :top)
                           (css/add-class :padded)
                           (css/add-class :vertical))

                      (my-dom/div
                        (->> (css/grid-column)
                             (css/grid-column-size {:small 4 :medium 3}))
                        (photo/square
                          {:src (:item/img-src i)}))

                      (my-dom/div
                        (->> (css/grid-column)
                             (css/grid-column-size {:small 5 :medium 7}))

                        (dom/div #js {:className "content-item-title-section"}

                          (dom/a #js {:href      (str "/goods/" (:db/id i))
                                      :className "name"} (:item/name i))))
                      (my-dom/div
                        (->> (css/grid-column)
                             (css/grid-column-size {:small 3 :medium 2})
                             (css/text-align :right))
                        (dom/div #js {:className "content-item-subtitle-section"}
                          (dom/span #js {:className "price"}
                                    (utils/two-decimal-price (:item/price i)))))))
                  items))
      (my-dom/div
        (->> (css/grid-column)
             (css/text-align :right))
        (dom/div #js {:className "total-price-section"}
          (let [item-price (compute-item-price items)
                shipping-price 0]
            (dom/table
              nil
              (dom/tbody
                nil
                (dom/tr nil
                        (dom/td nil "Item Price")
                        (dom/td nil (utils/two-decimal-price item-price)))
                (dom/tr nil
                        (dom/td nil "Shipping")
                        (dom/td nil (utils/two-decimal-price shipping-price)))
                (dom/tr #js {:className "total-price"}
                        (dom/td nil "Total")
                        (dom/td nil (utils/two-decimal-price (+ item-price shipping-price))))))))
        (dom/a #js {:className "button success"} "Checkout")))))


(defui Checkout
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [{:cart/items [:item/name
                                 :item/price
                                 :item/img-src
                                 {:item/store [:store/name :store/photo :store/review-count :store/rating]}]}]}])
  Object
  (render [this]
    (let [{:keys [query/cart proxy/navbar]} (om/props this)
          {:keys [cart/items]} cart
          item-count (count items)]
      (common/page-container
        {:navbar navbar}
        (my-dom/div
          (-> (css/grid-column)
              css/grid-row)
          (if (not-empty items)
            (dom/div nil
              (apply dom/div nil
                     (map (fn [[s its]]
                            (store-checkout-element s its))
                          (items-by-store items)))
              (when (< 1 (count (items-by-store items)))
                (dom/div #js {:className "callout transparent"}
                  (dom/h4 nil "or checkout all stores")
                  (my-dom/div
                    (->> (css/grid-row)
                         (css/align :bottom)
                         css/callout)
                    (apply my-dom/div
                           (css/grid-column)
                           (map (fn [[s its]]
                                  (my-dom/div
                                    (css/grid-row)
                                                  ;{:classes [::css/grid-row-align-middle :padded :vertical]}
                                    (my-dom/div
                                      (->> (css/grid-column)
                                           (css/grid-column-size {:small 2}))
                                      (photo/square
                                        {:src (:store/photo s)}))
                                    (my-dom/div
                                      (css/grid-column)
                                      (dom/a #js {:href (str "/store/" (:db/id s))}
                                             (dom/p #js {:className "store-name"} (:store/name s)))
                                      (common/rating-element (:store/rating s) (:store/review-count s)))

                                    (my-dom/div
                                      (-> (css/text-align :right)
                                          css/grid-column)
                                      (dom/span #js {:className "price"}
                                                (str (count its) " items: " (utils/two-decimal-price (compute-item-price its)))))))
                                (items-by-store items)))

                    (my-dom/div
                      (->> (css/grid-column)
                           (css/grid-column-size {:small 12
                                            :medium      4})
                           (css/text-align :right))
                      (dom/table nil
                                 (dom/tbody nil
                                            (dom/tr nil
                                                    (dom/td nil "Item Price")
                                                    (dom/td nil (utils/two-decimal-price (compute-item-price items))))
                                            (dom/tr nil
                                                    (dom/td nil "Shipping")
                                                    (dom/td nil (utils/two-decimal-price 0)))
                                            (dom/tr #js {:className "total-price"}
                                                    (dom/td nil (dom/h5 nil "Total"))
                                                    (dom/td nil (dom/h5 nil (utils/two-decimal-price (compute-item-price items)))))))
                      (dom/a #js {:className "button success"}
                             "Checkout All Stores"))))))
            #?(:cljs
                    (dom/div #js {:className "cart-empty callout text-center"}
                      (dom/h3 nil "Your shopping bag is empty")
                      (dom/a #js {:href "/"} (dom/h5 nil "Go to the market - start shopping")))
               :clj (dom/div {:className "cart-loading text-center"}
                      (dom/i {:className "fa fa-spinner fa-spin fa-4x"})))))))))

(def ->Checkout (om/factory Checkout))