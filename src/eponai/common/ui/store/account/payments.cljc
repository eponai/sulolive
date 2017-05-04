(ns eponai.common.ui.store.account.payments
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.icons :as icons]
    [om.next :as om]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.callout :as callout]))



(defn payment-methods [component]
  (dom/div
    nil
    (callout/callout-small
      (css/add-class :warning)
      (dom/p nil (dom/small nil "Settings are under development and received payments cannot be shown yet. Excuse the mess, thank you for understanding.")))
    (callout/callout-small
      nil
      (callout/header nil "Supported payment methods")
      (dom/div
        (css/add-class :payment-methods)
        (icons/visa-card)
        ;(icons/mastercard)
        (dom/div (css/add-class :icon)
                 (dom/img {:src "/assets/img/mc_vrt_opt_pos_45_3x.png"}))
        (icons/american-express)))

    (callout/callout-small
      nil
      (callout/header
        nil "Received payments")
      (dom/div
        (css/add-class :empty-container)
        (dom/span (css/add-class :shoutout) "No received payments")))
    ))

