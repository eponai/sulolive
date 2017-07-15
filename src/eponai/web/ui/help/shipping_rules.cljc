(ns eponai.web.ui.help.shipping-rules
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [om.next :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.photo :as photo]
    [eponai.common.routes :as routes]
    [om.next :as om]))

(defui ShippingRules
  Object
  (render [this]
    (dom/div
      nil
      (dom/p nil "Before setting up your shipping rates, you need to decide on the following")
      (dom/ul nil (dom/li nil "Where you will be shipping orders to (called shipping areas)")
              (dom/li nil "How many different rates you need for each area (called shipping rates)"))
      (dom/h2 nil "Shipping rules")
      (dom/p nil (dom/span nil "Shipping options are setup using ")
             (dom/i nil "shipping rules")
             (dom/span nil ". Shipping rules combine an area and a rate for you so that it's easy to see what your different areas and rates are. Once you use a rule to create rates, you can continue to add more rates to that area, or you can create another shipping rule to set up a new area."))

      (dom/h3 nil "Shipping areas")
      (dom/p nil (dom/span nil "It's likely that you'll charge more the farther away an order is shipped, so SULO Live allows you to set shipping rates for groups of destinations called shipping areas. The areas will each have different rates, allowing you to tailor your rates to specific regions. You can choose entire continents (like all of North America or all of Europe) or individual countries."))

      (dom/h3 nil "Shipping rates")
      (dom/p nil (dom/span nil "Once you decide where you're going to ship, you need to decide how much to charge and what to base it on. Shipping rates are the actual charges to your customers. You can make the rate a flat rate (one price for all orders shipped to that destination), or you can charge per item in an order."))
      (dom/p nil (dom/span nil "You can have more than one rate for each shipping area. For example, you might have one rate for standard shipping and another for overnight shipping."))

      (dom/h2 nil "Use rules to create rates")
      (dom/p nil (dom/span nil "Shipping rules provide functionality to get your first rates set up for a shipping area. You can add more rates once you've used the rule"))
      (dom/ol
        nil
        (dom/li nil (dom/p nil (dom/span nil "Navigate to manage shipping via the left menu.")))
        (dom/li
          nil
          (dom/p nil (dom/span nil "Click 'Add shipping rule'")
                 (dom/br nil )
                 (dom/small nil "Shipping rules help you get started creating rates for a shipping area. You add a rule, and then add more rates as needed")))
        (dom/li
          nil
          (dom/p nil (dom/span nil "In the 'Select destinations' dialog, start enter a locations to see what's available. Select the locations you want to include in this area. Your selection will become the name of the shipping rate.")
                 (dom/br nil )
                 (dom/strong nil (dom/small nil "Tip: "))
                 (dom/small nil "If you've already added a location to an area, that location won't show up in the field.")))

        (dom/li
          nil
          (dom/p nil (dom/span nil "Click 'Next' to add the first rate for this group of destinations.")))

        (dom/li
          nil
          (dom/p nil (dom/span nil "Enter a name for the rate. This will be shown to customers on the cart and order pages so it should be descriptive, but not too long. For example, if you're creating a rate for priority shipping via the Canada Post, you might name it Canada Post Priority.")))

        (dom/li
          nil
          (dom/p nil (dom/span nil "Provide any additional information to the customer if necessary. For examle if they need to sign the delivery in person.")))

        (dom/li
          nil
          (dom/p nil (dom/span nil "Add a rate for the first order item in 'Rate first item'. If you want to charge more per additional item after the first, provide that rate in 'Rate additional item' box.")))

        (dom/li
          nil
          (dom/p nil (dom/span nil "Select if you want to offer free shipping when the order is above a certain amount. Your customer will be presented with the information if they are about to place an order below that amount."))))


      )))

(def ->ShippingRules (om/factory ShippingRules))