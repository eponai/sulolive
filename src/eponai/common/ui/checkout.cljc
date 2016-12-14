(ns eponai.common.ui.checkout
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]))

(defn items-by-store [items]
  (group-by :item/store items))

(defn compute-item-price [items]
  (reduce + (map :item/price items)))

(defn store-element [s]
  (dom/div #js {:className "row content-items-container store-container align-middle"}
    (dom/div #js {:className "columns small-2 medium-1"}
      (common/photo-element {:class "square" :url (:store/photo s)}))
    (dom/div #js {:className "columns"}
      (dom/a #js {:href (str "/store/" (:db/id s))}
             (dom/p #js {:className "store-name"} (:store/name s)))
      (common/rating-element (:store/rating s) (:store/review-count s)))))

(defn store-checkout-element [s items]
  (dom/div #js {:className "callout cart-checkout-item"}
    (store-element s)

    (dom/div #js {:className "row align-bottom"}
      (apply dom/div #js {:className "column small-12 medium-8"}
             (map (fn [i]
                    (dom/div #js {:className "row cart-items align-top"}
                      (dom/div #js {:className "columns small-4 medium-3"}
                        (common/photo-element {:class "square"
                                        :url   (:item/img-src i)}))
                      (dom/div #js {:className "columns small-5 medium-7"}
                        (dom/div #js {:className "content-item-title-section"}
                          (dom/span #js {:className "name"} (:item/name i))))
                      (dom/div #js {:className "columns small-3 medium-2 text-right"}
                        (dom/div #js {:className "content-item-subtitle-section"}
                          (dom/span #js {:className "price"}
                                    (utils/two-decimal-price (:item/price i)))))))
                  items))
      (dom/div #js {:className "column clearfix text-right"}
        (dom/div #js {:className "total-price-section"}
          (let [item-price (compute-item-price items)
                shipping-price 0]
            (dom/table nil
                       (dom/tbody nil
                                  (dom/tr nil
                                          (dom/td nil "Item Price")
                                          (dom/td nil (utils/two-decimal-price item-price)))
                                  (dom/tr nil
                                          (dom/td nil "Shipping")
                                          (dom/td nil (utils/two-decimal-price shipping-price)))
                                  ;(dom/tr nil
                                  ;        (dom/td nil ))
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
      (debug "GOT CART: " cart)
      (common/page-container
        {:navbar navbar}
        (dom/div #js {:className "row column checkout-container"}
          (if (not-empty items)
            (dom/div nil
              ;(dom/h4 nil (if (= 1 item-count)
              ;              "1 item in your shopping bag"
              ;              (str (count items) " items in your shopping bag")))
              (apply dom/div nil
                     (map (fn [[s its]]
                            (store-checkout-element s its))
                          (items-by-store items)))
              (dom/div #js {:className "callout transparent"}
                (dom/h4 nil "or checkout all stores")
                (dom/div #js {:className "row callout"}
                  (apply dom/div #js {:className "column"}
                         (map (fn [[s its]]
                                (dom/div #js {:className "row content-items-container store-container align-middle"}
                                  (dom/div #js {:className "columns small-2 medium-2"}
                                    (common/photo-element {:class "square" :url (:store/photo s)}))
                                  (dom/div #js {:className "columns"}
                                    (dom/a #js {:href (str "/store/" (:db/id s))}
                                           (dom/p #js {:className "store-name"} (:store/name s)))
                                    (common/rating-element (:store/rating s) (:store/review-count s)))
                                  (dom/div #js {:className "columns text-right "}
                                    ;(dom/p nil (str (count its) " items"))
                                    (dom/span #js {:className "price"} (str (count its) " items: " (utils/two-decimal-price (compute-item-price its)))))))
                              (items-by-store items))
                    )
                  (dom/div #js {:className "column small-12 medium-4 text-right"}
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
                                                  (dom/td nil (dom/h5 nil (compute-item-price items))))))
                    (dom/a #js {:className "button success"}
                           "Checkout All Stores")))))
            #?(:cljs
                    (dom/div #js {:className "cart-empty callout text-center"}
                      (dom/h3 nil "Your shopping bag is empty")
                      (dom/a #js {:href "/"} (dom/h5 nil "Go to the market - start shopping")))
               :clj (dom/div {:className "cart-loading text-center"}
                      (dom/i {:className "fa fa-spinner fa-spin fa-4x"})))))))))

(def ->Checkout (om/factory Checkout))