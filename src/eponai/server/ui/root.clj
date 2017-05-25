(ns eponai.server.ui.root
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.server.ui.common :as common]
    [taoensso.timbre :refer [debug]]
    [clojure.data.json :as json]))

(defui Root
  Object
  (render [this]
    (let [{:keys [release? ::app-html route auth cljs-build-id social-sharing]} (om/props this)]
      (debug "app-html: " app-html)
      (dom/html
        {:lang "en"}
        (common/head {:release?       release?
                      :social-sharing social-sharing
                      :cljs-build-id  cljs-build-id})
        (dom/body
          nil
          (dom/div {:height "100%" :id router/dom-app-id}
            app-html)

          ; (common/auth0-lock-passwordless release?)
          (common/auth0-lock release?)

          (dom/script {:src     "https://cdn.greta.io/greta.min.js"
                       :type    common/text-javascript
                       :id      "gretaScript"
                       :data-ac (if release?
                                  "b554c0b026bb448362dfe657846bf982"
                                  "fe5ab77ade7eee2c0d3e1e8f51692a7f")
                       ;; Using data-lazy right now because we have etsy images on our site
                       ;; which doesn't have CORS set up (for good reason).
                       ;:data-lazy "true"
                       })
          (dom/script {:src "//assets.pinterest.com/js/pinit.js"
                       :async true
                       :defer true
                       :type common/text-javascript})
          (dom/script {:src  (common/budget-js-path)
                       :type common/text-javascript})

          (when (some? (:user-id auth))
            (common/inline-javascript [(str "mixpanel.identify(\"" (:user-id auth) "\");")
                                       (str "mixpanel.people.set_once(" (json/write-str {:$email (:email auth) :$last_name (:email auth)}) ");")]))

          (when-not (= cljs-build-id "devcards")
            (common/inline-javascript ["env.web.main.runsulo()"])))))))
