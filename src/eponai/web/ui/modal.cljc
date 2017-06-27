(ns eponai.web.ui.modal
  (:require [eponai.common.ui.dom :as dom]
            [eponai.common.ui.elements.css :as css]))

(defn modal [opts & content]
  (let [{:keys [classes on-close size require-close? id]} opts]
    (dom/div
      (->> {:id      "reveal-overlay"
            :onClick #(when (= "reveal-overlay" (.-id (.-target %)))
                       (when (and on-close (not require-close?))
                         (on-close)))}
           (css/add-class :reveal-overlay))
      (dom/div
        (css/add-class (str "reveal " (when (some? size) (name size))) {:classes classes
                                                                        :id id})
        (when on-close
          (dom/a
            (css/add-class :close-button {:onClick on-close})
            (dom/span nil "x")))
        content))))