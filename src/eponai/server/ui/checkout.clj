(ns eponai.server.ui.checkout
  (:require
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.checkout :as checkout]
    [eponai.common.ui.common :as cljc-common]
    [eponai.server.ui.common :as common]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Checkout
  Object
  (render [this]
    (let [{:keys [release? :eponai.server.ui/render-component-as-html]} (om/props this)]
      (dom/html
        {:lang "en"}
        (apply dom/head nil (common/head release?))
        (dom/body
          nil
          (dom/div {:height "100%" :id "the-sulo-app"}
            (render-component-as-html checkout/Checkout))
          (common/auth0-lock-passwordless release?)
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})

          (common/inline-javascript ["env.web.main.runcheckout()"])
          )))))