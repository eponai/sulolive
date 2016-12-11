(ns eponai.server.ui
  (:require
    [om.next :as om]
    [om.dom :as dom]
    [eponai.server.auth :as auth]
    [eponai.server.ui.goods :as goods]
    [eponai.server.ui.product :as product]
    [eponai.server.ui.index :as index]
    [eponai.server.ui.store :as store]
    [eponai.server.ui.checkout :as checkout]))

(defn with-doctype [html-str]
  (str "<!DOCTYPE html>" html-str))

(defn render-to-str [component props]
  {:pre [(some? (:release? props))]}
  (with-doctype (dom/render-to-str ((om/factory component) props))))

(defn makesite [component]
  (fn [props]
    (let [component-props ((::component->props-fn props) component)
          ret (render-to-str component (merge component-props
                                              (dissoc props ::component->props-fn)))]
      ret)))

(def auth-html (makesite auth/Auth))
(def goods-html (makesite goods/Goods))
(def product-html (makesite product/Product))
(def index-html (makesite index/Index))
(def store-html (makesite store/Store))
(def checkout-html (makesite checkout/Checkout))