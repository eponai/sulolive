(ns eponai.common.ui.elements.menu
  (:require
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]))

;; Menu elements
(defn- menu* [{:keys [formats]} & content]
  (apply dom/ul #js {:className (css/keys->class (conj formats ::css/menu))}
         content))

(defn horizontal [{:keys [formats]} & content]
  (apply menu*
         {:formats formats}
         content))

(defn vertical [{:keys [formats]} & content]
  (apply menu*
         {:formats (conj formats ::css/menu-vertical)}
         content))

;; Menu list item elements
(defn- item* [{:keys [formats]} & content]
  (apply
    dom/li #js {:className (css/keys->class formats)}
    content))

(defn tab [{:keys [active? on-click]} & content]
  (item*
    {:formats (when active? [::css/menu-active])}
    (apply dom/a
           #js {:onClick on-click}
           content)))

(defn link [{:keys [href]} & content]
  (item*
    nil
    (apply dom/a
           #js {:href href}
           content)))

(defn text [opts & content]
  (apply item*
         {:formats [::css/menu-text]}
         content))