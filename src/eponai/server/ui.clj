(ns eponai.server.ui
  (:require
    [om.next :as om]
    [om.dom :as dom]
    [eponai.server.ui.app :as app]
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
  `(let [render-fn# (partial render-to-str ~component)
         mem-fn# (memoize render-fn#)
         props# (gensym)]
     (defn ~name [props#]
       (if (:release? props#)
         (mem-fn# props#)
         (render-fn# props#)))))

;; These will be defined by the defsite macro.
(declare app-html index-html signup-html terms-html store-html)

(defsite app-html app/App)
(defsite index-html index/Index)
(defsite signup-html signup/Signup)
(defsite store-html store/Store)
(defsite terms-html terms/Terms)