(ns eponai.web.ui.unauthorized
  (:require
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [om.dom :as dom]))

(defui Unauthorized
  static om/IQuery
  (query [this]
    [:query/messages])
  Object
  (render [this]
    (dom/div
      nil
      (dom/strong nil "UNAUTHORIZED :("))))

(def ->Unauthorized (om/factory Unauthorized))

(router/register-component :unauthorized Unauthorized)