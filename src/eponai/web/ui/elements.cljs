(ns eponai.web.ui.elements
  (:require
    [clojure.string :as s]
    [eponai.web.ui.elements.css-classes :as css]
    [om.dom :as dom]
    [sablono.core :refer-macros [html]]))

(defn button [{:keys [classes on-click]} title]
  (let [button-classes (conj classes ::css/button)]
    (dom/a
      #js {:className (css/class-names button-classes)
           :onClick   on-click}
      title)))

(defn button-primary [{:keys [classes on-click]} title]
  (button
    {:classes (conj classes ::css/primary)
     :on-click on-click}
    title))

(defn switch [{:keys [id]}]
  (dom/div
    #js {:className (css/class-names [::css/switch ::css/tiny])}
    (dom/input
      #js {:id id
           :className (css/class-names [::css/switch-input])
           :type "checkbox"
           :name id})
    (dom/label
      #js {:className (css/class-names [::css/switch-paddle])
           :htmlFor id})))

(defn menu [& children]
  (dom/ul
    #js {:className "menu"}
    (html
      children)))

(defn menu-item [& children]
  (dom/li
    nil
    (html
      children)))