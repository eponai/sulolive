(ns eponai.common.checkout-utils
  (:require
    [taoensso.timbre :refer [debug]]))

(defn compute-subtotal [skus]
  (or (reduce + (map #(get-in % [:store.item/_skus :store.item/price]) skus)) 0))

(defn compute-shipping-fee [rate items]
  (let [{:shipping.rate/keys [additional free-above]
         first-rate          :shipping.rate/first} rate
        subtotal (compute-subtotal items)
        item-count (count items)]
    (cond (and (some? free-above)
               (> subtotal free-above))
          0
          (<= 1 item-count)
          (apply + (or first-rate 0) (repeat (dec item-count) (or additional 0)))
          :else
          (or first-rate 0))))

(defn compute-taxes [taxes subtotal shipping-fee]
  (let [{tax-rate    :taxes/rate
         :taxes/keys [freight-taxable?]} taxes
        tax-rate (or tax-rate 0)]

    (if freight-taxable?
      (* tax-rate (+ subtotal shipping-fee))
      (* tax-rate subtotal))))

(defn compute-discount [price coupon]
  (if (:valid coupon)
    (let [percent-off (:percent_off coupon)]
      (* 0.01 percent-off price))
    0))

(def sulo-fee-percent 20)

(defn coupon-percent-off [coupon]
  (or (:percent_off coupon) 0))

(defn compute-checkout [{:keys [skus taxes shipping-rate coupon]}]
  (let [subtotal (compute-subtotal skus)
        shipping-amount (compute-shipping-fee shipping-rate skus)
        discount (compute-discount subtotal coupon)
        tax-amount (compute-taxes taxes (- subtotal discount) shipping-amount)
        sulo-fee (* 0.01 (- sulo-fee-percent (coupon-percent-off coupon)) subtotal)]

    (debug "Computed checkout: " {:subtotal        subtotal
                                  :shipping-amount shipping-amount
                                  :tax-amount      tax-amount
                                  :discount        discount
                                  :grandtotal      (- (+ subtotal tax-amount shipping-amount) discount)
                                  :sulo-fee        sulo-fee})
    {:subtotal        subtotal
     :shipping-amount shipping-amount
     :tax-amount      tax-amount
     :discount        discount
     :grandtotal      (- (+ subtotal tax-amount shipping-amount) discount)
     :total           (+ subtotal tax-amount shipping-amount)
     :sulo-fee        sulo-fee}))