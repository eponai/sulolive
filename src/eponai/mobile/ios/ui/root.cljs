(ns eponai.mobile.ios.ui.root
  (:require
    [eponai.mobile.components :refer [view text ]]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(defui Root
  Object
  (render [this]
    (debug "Render ")
    (view nil (text nil "This is an example"))))

(def ->Root (om/factory Root))
