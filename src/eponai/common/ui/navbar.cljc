(ns eponai.common.ui.navbar
  (:require
    #?(:cljs [goog.string :refer [format]])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Navbar
  static om/IQuery
  (query [_]
    [{:query/cart [:cart/price
                   :cart/items]}])
  Object
  (initLocalState [_]
    {:cart-open? false})
  (render [this]
    (let [{:keys [cart-open?]} (om/get-state this)
          {:keys [query/cart]} (om/props this)]
      (prn "CART " cart)
      (dom/div #js {:id "sulo-navbar"}
        (dom/nav #js {:className "navbar top-bar"}
                 (dom/div #js {:className "top-bar-left"}
                   (dom/ul #js {:className "menu"}
                           (dom/li nil
                                   (dom/a #js {:className "navbar-brand"
                                               :href      "/"}
                                          "Sulo"))))

                 (dom/div #js {:className "top-bar-right shopping-cart"}
                   (dom/ul #js {:className "menu"}
                           (dom/li nil
                                   (dom/a #js {:href "/checkout"
                                               ;:onClick #(om/update-state! this update :cart-open? not)
                                               }
                                          (dom/span nil (format "$%.2f" (double (:cart/price cart))))
                                          (dom/i #js {:className "fa fa-shopping-cart fa-fw"}))))

                   ;(when cart-open?)
                   (dom/div #js {:className "cart-container dropdown-pane"}
                     (dom/div nil
                       (apply dom/ul #js {:className "cart menu vertical"}
                              (map (fn [i]
                                     (dom/li nil
                                             (dom/div #js {:className "row collapse align-middle content-item"}
                                               (dom/div #js {:className "columns small-2"}
                                                 (dom/div #js {:className "photo-container"}
                                                   (dom/div #js {:className "photo square thumbnail" :style #js {:backgroundImage (str "url(" (:item/img-src i) ")")}})))
                                               (dom/div #js {:className "columns small-10"}
                                                 (dom/div #js {:className "content-item-title-section"}
                                                   (dom/small #js {:className "name"} (:item/name i)))
                                                 (dom/div #js {:className "content-item-subtitle-section"}
                                                   (dom/span #js {:className "price"} (:item/price i)))))))
                                   (take 3 (:cart/items cart))))

                       (dom/div #js {:className "callout nude"}
                         (when (< 3 (count (:cart/items cart)))
                           (dom/small nil (str "You have " (- (count (:cart/items cart)) 3) " more item(s) in your bag")))
                         (dom/h5 nil "Total: " (dom/strong nil (format "$%.2f" (double (:cart/price cart))))))
                       (dom/a #js {:className "button expanded"
                                   :href      "/checkout"} "View My Bag")))))))))

(def ->Navbar (om/factory Navbar))