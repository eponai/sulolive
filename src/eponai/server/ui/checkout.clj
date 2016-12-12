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
     {:proxy/cart (om/get-query checkout/Checkout)}])
  Object
  (render [this]
    (let [{:keys [release? proxy/cart proxy/navbar]} (om/props this)]
      (dom/html
        {:lang "en"}
        (apply dom/head nil (common/head release?))
        (dom/body
          nil
          (dom/div
            {:id "sulo-checkout"
             :className "page-container"}
            (nav/navbar navbar)
            (dom/div {:className "page-content" :id "sulo-checkout-container"}
              (checkout/->Checkout cart))
            (common/footer nil))
          (dom/script {:src "https://cdn.auth0.com/js/lock-passwordless-2.2.min.js"})
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})

          (common/inline-javascript ["env.web.main.runcheckout()"])
          )))))