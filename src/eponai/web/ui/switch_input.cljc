(ns eponai.web.ui.switch-input
  (:require [eponai.common.ui.dom :as dom]
            [eponai.common.ui.elements.css :as css]))

(defn switch [{:keys [title id classes] :as opts} & content]
  (dom/div
    (css/add-class :switch-container)
    (dom/div
      {:classes (conj classes :switch)}
      (dom/input (->> (merge opts
                             {:type "checkbox"
                              :name id})
                      (css/add-class :switch-input)))
      (dom/label
        (css/add-class :switch-paddle {:htmlFor id})
        (dom/span (css/show-for-sr) title)
        content))))