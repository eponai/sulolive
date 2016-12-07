(ns eponai.common.ui.checkout
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Checkout
  Object
  (render [this]
    (dom/div nil "This is checkout")))

(def ->Checkout (om/factory Checkout))