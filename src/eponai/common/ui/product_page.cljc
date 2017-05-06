(ns eponai.common.ui.product-page
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.product :as product]
    [taoensso.timbre :refer [debug]]))


(defui ProductPage
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/item (om/get-query product/Product)}])
  Object
  (render [this]
    (let [{:keys [query/item proxy/navbar]} (om/props this)]
      (common/page-container
        {:navbar navbar :id "sulo-product-page"}
        (product/->Product item)))))

(def ->ProductPage (om/factory ProductPage))

(router/register-component :product ProductPage)