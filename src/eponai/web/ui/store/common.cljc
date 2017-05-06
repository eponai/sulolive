(ns eponai.web.ui.store.common
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]))

(defn edit-button [opts & content]
  (dom/a
    (->> (css/button-hollow opts)
         (css/add-class :shrink)
         (css/add-class :secondary))
    (dom/i {:classes ["fa fa-pencil  fa-fw"]})
    (if (not-empty content)
      content
      (dom/span nil "Edit"))))

(defn save-button [opts & content]
  (dom/a (css/button opts)
         (dom/span nil "Save")))

(defn cancel-button [opts & content]
  (dom/a (css/button-hollow opts)
         (dom/span nil "Cancel")))