(ns eponai.common.ui.dom
  (:require
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]))

(defn element [el-fn {:keys [classes] :as opts} & content]
  (let [react-opts (-> opts
                       (dissoc :classes)
                       (assoc :className (css/keys->class-str classes)))]
    (apply el-fn
           #?(:cljs (clj->js react-opts)
              :clj  react-opts)
           content)))

(defn div [opts & content]
  (element dom/div opts content))

(defn a [opts & content]
  (element dom/a opts content))

(defn li [opts & content]
  (element dom/li opts content))

(defn ul [opts & content]
  (element dom/ul opts content))