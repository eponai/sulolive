(ns eponai.common.analytics.google
  (:require
    [taoensso.timbre :refer [error debug]]))

(def ga-commands
  {::impression "ec:addImpression"
   ::product    "ec:addProduct"})

(def ga-actions
  {::click           "click"
   ::detail          "detail"
   ::add             "add"
   ::remove          "remove"
   ::checkout        "checkout"
   ::checkout-option "checkout_option"
   ::purchase        "purchase"})

(defn set-cad []
  #?(:cljs
     (js/ga "set" "currencyCode" "CAD")))

(defn send-pageview []
  #?(:cljs
     (js/ga "send" "pageview")))

(defn send-event [source type label]
  #?(:cljs
     (js/ga "send" "event" source type label)))

(defn set-action [action-key & [opts]]
  #?(:cljs
     (if-let [action (get ga-actions action-key)]
       (if (not-empty opts)
         (js/ga "ec:setAction" action (clj->js (merge {}
                                                      opts)))
         (js/ga "ec:setAction" action))

       (error "No valid ga action specified, should be one of " (keys ga-actions)))))

(defn add-product [command product & [opts]]
  #?(:cljs
     (if-let [c (get ga-commands command)]
       (let [store (:store/_items product)
             category (:store.item/category product)]
         (js/ga c (clj->js (merge {:id       (:db/id product)
                                   :name     (:store.item/name product)
                                   :brand    (:store.profile/name (:store/profile store))
                                   :quantity 1
                                   :category (:category/name category)}
                                  opts))))
       (error "No ga command type was specified when collecting product, should be one of " (keys ga-commands)))))

(defn add-product-impression [store product & [opts]]
  #?(:cljs
     (let [category (:store.item/category product)]
       (js/ga "ec:addImpression" (clj->js (merge {:id       (:db/id product)
                                                  :name     (:store.item/name product)
                                                  :brand    (:store.profile/name (:store/profile store))
                                                  :list     "Search results"
                                                  :category (:category/name category)}
                                                 opts))))))

(defn add-product-action [store sku]
  #?(:cljs
     (let [product (:store.item/_skus sku)
           category (:store.item/category product)]
       (js/ga "ec:addProduct" #js {:id       (:db/id product)
                                   :name     (:store.item/name product)
                                   :brand    (:store.profile/name (:store/profile store))
                                   :price    (:store.item/price product)
                                   :variant  (:store.item.sku/variation sku)
                                   :category (:category/name category)}))))

(defn send-product-list-impressions [list products]
  (doseq [p products]
    (add-product ::impression p {:list list}))
  (send-pageview))


(defn send-product-detail-view [product]
  (add-product ::product product)
  (set-action ::detail)
  (send-pageview))

(defn send-add-to-bag [product sku]
  (debug "Send add to bag event: " sku)
  (add-product ::product product {:variant  (:store.item.sku/variation sku)
                                  :price    (:store.item/price product)
                                  :quantity 1})
  (set-action ::add)
  (send-event "UX" "click" "add to cart"))

(defn send-remove-from-bag [product sku]
  (debug "Send remove from bag: " sku)
  (add-product ::product product {:variant  (:store.item.sku/variation sku)
                                  :quantity 1})
  (set-action ::remove)
  (send-event "UX" "click" "remove from cart"))

(defn checkout-skus [skus]
  (doseq [sku skus]
    (let [product (:store.item/_skus sku)]
      (debug "ga: Add product")
      (add-product ::product product {:variant (:store.item.sku/variation sku)
                                      :price   (:store.item/price product)}))))

(defn send-checkout-option-event []
  #?(:cljs
     (js/ga "send" "event" "Checkout" "Option")))

;(defn checkout [skus country]
;  (checkout-skus checkout)
;  (set-action ::ga/checkout {:step 1 :option country})
;  (send-pageview))

(defn checkout-shipping-address [skus country]
  (debug "Send shipping address: " country)
  (checkout-skus skus)
  (set-action ::checkout {:step 1 :option country})
  (send-pageview))

(defn checkout-shipping-option [option]
  (debug "Send shipping option: " option)
  ;(checkout-skus skus)
  (set-action ::checkout-option {:step 1 :option option})
  (send-checkout-option-event))

(defn checkout-payment-details [skus option]
  (debug "Send payment option: " option)
  ;(checkout-skus skus)
  (set-action ::checkout {:step 2 :option option})
  (send-pageview))

(defn checkout-payment-option [skus option]
  (debug "Send payment option: " option)
  ;(checkout-skus skus)
  (set-action ::checkout {:step 2 :option option})
  (send-checkout-option-event))

(defn send-transaction [skus opts]
  (set-cad)
  (checkout-skus skus)
  (set-action ::purchase opts)
  (send-pageview))