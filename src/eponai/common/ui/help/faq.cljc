(ns eponai.common.ui.help.faq
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]))

(defui FAQ
  Object
  (render [this]
    (dom/div
      nil
      (dom/h1 nil "FAQ")
      (dom/p nil (dom/span nil "We haven't gotten any questions yet :(")))))

(def ->FAQ (om/factory FAQ))