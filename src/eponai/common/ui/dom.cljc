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

(defn strong [opts & content]
  (element dom/strong opts content))

(defn i [opts & content]
  (element dom/i opts content))

(defn span [opts & content]
  (element dom/span opts content))

(defn label [opts & content]
  (element dom/label opts content))

(defn h3 [opts & content]
  (element dom/h3 opts content))

(defn input [opts & content]
  (element dom/input opts content))