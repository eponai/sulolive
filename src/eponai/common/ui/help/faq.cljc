(ns eponai.common.ui.help.faq
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.css :as css]
    ))

(defui FAQ
  Object
  (render [this]
    (dom/div
      nil
      (dom/h1 nil "FAQ")
      (dom/p nil (dom/span nil "We haven't gotten any questions yet :(")))))

(def ->FAQ (om/factory FAQ))