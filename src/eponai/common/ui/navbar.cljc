(ns eponai.common.ui.navbar
  (:require
    #?(:cljs [goog.string :refer [format]])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Navbar
  static om/IQuery
  (query [_]
    [{:query/cart [:cart/price]}])
  Object
  (render [this]
    (let [{:keys [query/cart]} (om/props this)]
      (prn "CART " cart)
      (dom/div #js {:id "sulo-navbar"}
        (dom/nav #js {:className "navbar top-bar"}
                 (dom/div #js {:className "top-bar-left"}
                   (dom/ul #js {:className "menu"}
                           (dom/li nil
                                   (dom/a #js {:className "navbar-brand"
                                               :href      "/"}
                                          "Sulo"))))

                 (dom/div #js {:className "top-bar-right"}
                   (dom/ul #js {:className "menu"}
                           (dom/li nil
                                   (dom/a nil
                                          (dom/span nil (format "$%.2f" (double (:cart/price cart))))
                                          (dom/i #js {:className "fa fa-shopping-cart fa-fw"}))))))))))

(def ->Navbar (om/factory Navbar))