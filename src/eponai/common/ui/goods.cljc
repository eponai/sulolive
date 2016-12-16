(ns eponai.common.ui.goods
  (:require
    [cemerick.url :as url]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product-item :as pi]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.product :as product]))

(defui Goods
  static om/IQueryParams
  (params [_]
    #?(:cljs
       (let [href js/window.location.href
             {:strs [category search]} (:query (url/url href))]
         (debug "Got query: " (:query (url/url href)))
         {:category category
          :search search})))
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     `({:query/items ~(om/get-query product/Product)} {:category ~'?category
                                                       :search ~'?search})])
  Object
  (render [this]
    (let [{:keys [query/items proxy/navbar]} (om/props this)]
      #?(:cljs (debug "Got items to render: " (om/props this)))
      (common/page-container
        {:navbar navbar}
        (dom/div #js {:id "sulo-items-container"}
          (apply dom/div #js {:className "row small-up-2 medium-up-3"}
                 (map (fn [p]
                        (pi/->ProductItem (om/computed {:product p}
                                                       {:display-content (product/->Product p)}))
                        )
                      items)))))))

(def ->Goods (om/factory Goods))