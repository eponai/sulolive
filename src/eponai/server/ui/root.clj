(ns eponai.server.ui.root
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.server.ui.common :as common]
    [taoensso.timbre :refer [debug]]))

(defui Root
  Object
  (render [this]
    (let [{:keys [release? ::app-html]} (om/props this)]
      (debug "app-html: " app-html)
      (dom/html
        {:lang "en"}
        (apply dom/head nil (common/head release?))
        (dom/body
          nil
          (dom/div #js {:height "100%" :id router/dom-app-id}
            app-html)

          (common/red5pro-script-tags release?)
          (common/auth0-lock-passwordless release?)
          (dom/script {:src  (common/budget-js-path release?)
                       :type common/text-javascript})
          ;(dom/script {:src "https://cdn.auth0.com/js/lock/10.6/lock.min.js"})

          (common/inline-javascript ["env.web.main.runsulo()"]))))))
