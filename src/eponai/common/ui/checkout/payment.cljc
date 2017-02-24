(ns eponai.common.ui.checkout.payment
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui CheckoutPayment
  Object
  (render [this]
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
                              :placeholder "e.g. 123"}))))))))

(def ->CheckoutPayment (om/factory CheckoutPayment))

