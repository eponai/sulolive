(ns eponai.web.ui.utils.button
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [om.dom :as dom]
    [eponai.web.ui.utils.css-classes :as css]
    [sablono.core :refer-macros [html]]))

(defn button [options & children]
  (let [classes (:classes (css/add-class options ::css/button))]
    (html
      [:a
       (-> (dissoc options :classes)
           (update :class #(css/class-names classes %)))
       children])))

(defn primary [btn-fn]
  (fn [options & children]
    (btn-fn (css/add-class options ::css/primary) children)))

(defn black [btn-fn]
  (fn [options & children]
    (btn-fn (css/add-class options ::css/black) children)))

(defn alert [btn-fn]
  (fn [options & children]
    (btn-fn (css/add-class options ::css/alert) children)))

(defn hollow [btn-fn]
  (fn [options & children]
    (btn-fn (css/add-class options ::css/hollow) children)))

(defn tiny [btn-fn]
  (fn [options & children]
    (btn-fn (css/add-class options ::css/tiny) children)))