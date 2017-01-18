(ns eponai.common.ui.business
  (:require [om.next :as om :refer [defui]]
            [om.dom :as dom]
            [eponai.common.business.budget :as b]))

(defui Business
  static om/IQuery
  (query [this]
    [:query/business-model])
  Object
  (render [this]
    (dom/div nil "HEJ ;D")))

(def ->Business (om/factory Business))