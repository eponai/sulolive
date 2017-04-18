(ns eponai.common.ui.elements.callout
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]))

(defn callout [opts & content]
  (dom/div
    (css/callout opts)
    content))

(defn callout-small [opts & content]
  (callout
    (css/add-class :small opts)
    content))

(defn header [opts & content]
  (dom/p (css/add-class :header opts) content))
