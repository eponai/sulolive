(ns eponai.devcards.checkout-cards
  (:require-macros
    [devcards.core :refer [defcard]])
  (:require
    [eponai.common.ui.checkout.shipping :as s]
    [eponai.common.ui.checkout.payment :as p]
    [eponai.common.ui.checkout.review :as r]
    [om.dom :as dom]
    [om.next :as om]
    [taoensso.timbre :refer [debug]]))

(defn checkout-container [content]
  (dom/div #js {:id "sulo-checkout"}
    content))
(defcard
  Shipping
  (checkout-container
    (s/->CheckoutShipping)))

(defcard
  Payment
  (checkout-container
    (p/->CheckoutPayment)))

(defcard
  Review
  (let [computed {:checkout/payment  {:card {:brand     "Visa"
                                             :last4     "1234"
                                             :exp_month "04"
                                             :exp_year  "32"}}
                  :checkout/shipping {:address/full-name "Diana"
                                      :address/street1   "2121 3rd St"
                                      :address/locality  "San Francisco"
                                      :address/region    "CA"
                                      :address/country   "US"}
                  :checkout/items    [{:store.item/_skus {:store.item/name   "Pants"
                                                          :store.item/price  1234
                                                          :store.item/photos [{:photo/path "/assets/img/women-new.jpg"}]}
                                       :store.item.sku/type :sku}]}]
    (debug "Checkout: " computed)
    (checkout-container
      (r/->CheckoutReview (om/computed {} computed)))))