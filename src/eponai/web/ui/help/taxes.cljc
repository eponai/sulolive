(ns eponai.web.ui.help.taxes
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.callout :as callout]))

(defui Taxes
  Object
  (render [this]
    (dom/div
      nil
      (dom/h1 nil "Sales tax")
      (callout/callout
        nil
        (dom/p nil "As a venue, SULO Live cannot provide specific tax advice. It is up to each seller to research their local laws about collecting and reporting taxes for their SULO Live sales. Tax regulations can vary greatly by location. If you are unsure how to charge tax, we encourage you to consult an accountant or other expert in your area.")

        (dom/section
          nil
          (dom/h2 nil "Automatic tax calculations")
          (dom/p nil "SULO Live has integrated automatic tax calculations based on the location of your business and that of your customer:")
          (dom/ul nil
                  (dom/li nil
                          (dom/p nil
                                 (dom/span nil "If your customer's shipping address is ")
                                 (dom/strong nil "inside Canada")
                                 (dom/span nil ", tax will be calculated and added to their final amount at checkout. The tax calculation is made based on the province of your business and the province of your customer. Your SULO Live commission will not apply to the tax amount.")))
                  (dom/li nil (dom/p nil
                                     (dom/span nil "If your customer's shipping address is ")
                                     (dom/strong nil "outside Canada")
                                     (dom/span nil ", no tax will be charged as there is no requirement to do so.")))))

        (dom/section
          nil
          (dom/h2 nil "Manual tax rates")
          (dom/p nil "There is currently no support for adding tax rates by province, but might be in the future. If you want to handle taxes yourself, you can include your taxes in the price of your product. Keep in mind that the SULO Live commission is based on the product price, so make sure you charge enough if you choose this option.")))

      )))

(def ->Taxes (om/factory Taxes))