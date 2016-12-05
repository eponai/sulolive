(ns eponai.server.ui
  (:require
    [om.next :as om]
    [om.dom :as dom]
    [eponai.common.parser :as parser]
    [eponai.server.ui.app :as app]
    [eponai.server.ui.goods :as goods]
    [eponai.server.ui.product :as product]
    [eponai.server.ui.index :as index]
    [eponai.server.ui.signup :as signup]
    [eponai.server.ui.store :as store]
    [eponai.server.ui.terms :as terms]))

(defn with-doctype [html-str]
  (str "<!DOCTYPE html>" html-str))

(defn render-to-str [component props]
  {:pre [(some? (:release? props))]}
  (with-doctype (dom/render-to-str ((om/factory component) props))))

(defmacro defsite
  "Defines a function named name, that takes props and returns html."
  [name component]
  `(let [props# (gensym)]
     (defn ~name [props#]
       (let [component-props# ((::component->props-fn props#) ~component)]
         (render-to-str ~component (merge component-props#
                                          (dissoc props# ::component->props-fn)))))))

;; These will be defined by the defsite macro.
(declare app-html index-html signup-html terms-html store-html goods-html product-html)

(defsite app-html app/App)
(defsite goods-html goods/Goods)
(defsite product-html product/Product)
(defsite index-html index/Index)
(defsite signup-html signup/Signup)
(defsite store-html store/Store)
(defsite terms-html terms/Terms)