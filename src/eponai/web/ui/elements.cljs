(ns eponai.web.ui.elements
  (:require
    [clojure.string :as s]
    [eponai.web.ui.elements.css-classes :as css]
    [om.dom :as dom]))

(defn button [{:keys [classes on-click]} title]
  (let [button-classes (conj classes ::css/button)
        classnames (s/join " " (map #(get css/all-classnames %) button-classes))]
    (dom/a
      #js {:className classnames
           :onClick   on-click}
      title)))