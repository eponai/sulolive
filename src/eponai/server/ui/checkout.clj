(ns eponai.server.ui.checkout
  (:require
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.checkout :as checkout]
    [eponai.server.ui.common :as common]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Checkout
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [:cart/price
                   :cart/items]}])
  Object
  (render [this]
    (let [{:keys [release? proxy/store proxy/navbar]} (om/props this)]
      (dom/html
        {:lang "en"}
        (apply dom/head nil (common/head release?))
        (dom/body
          nil
          (dom/div
            {:id "sulo-checkout"
             :className "page-container"}
            (nav/->Navbar navbar)
            (dom/div {:className "page-content" :id "sulo-checkout-container"}
              (checkout/->Checkout))
            (common/footer nil))
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})

          (common/inline-javascript ["env.web.main.runnavbar()"])
          (common/inline-javascript ["env.web.main.runcheckout()"])
          )))))