(ns eponai.common.ui.navbar
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Navbar
  Object
  (render [this]
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
                                        (dom/span nil "$0.00")
                                        (dom/i #js {:className "fa fa-shopping-cart fa-fw"})))))))))

(def ->Navbar (om/factory Navbar))