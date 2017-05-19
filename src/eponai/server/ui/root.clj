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
    (let [{:keys [release? ::app-html route cljs-build-id social-sharing]} (om/props this)]
      ;(debug "ROOT PROPS: " (om/props this))
      (debug "app-html: " app-html)
      (debug "ROUTE: " route)
      (debug "SHARING: " social-sharing)
      (dom/html
        {:lang "en"}
        (common/head {:release?       release?
                      :social-sharing social-sharing
                      :cljs-build-id  cljs-build-id})
        (dom/body
          nil
          (dom/div {:height "100%" :id router/dom-app-id}
            app-html)

          (common/auth0-lock-passwordless release?)
          ;(common/auth0-lock release?)

          (dom/script {:src     "https://cdn.greta.io/greta.min.js"
                       :type    common/text-javascript
                       :id      "gretaScript"
                       :data-ac "b554c0b026bb448362dfe657846bf982"
                       ;; Using data-lazy right now because we have etsy images on our site
                       ;; which doesn't have CORS set up (for good reason).
                       ;:data-lazy "true"
                       })
          ;(common/inline-javascript ["window.fbAsyncInit = function()"
          ;                           " {FB.init({ appId      : '1791653057760981', "
          ;                           "xfbml      : true,   version    : 'v2.9'   });"
          ;                           "FB.AppEvents.logPageView(); }; (function(d, s, id)"
          ;                           "{var js, fjs = d.getElementsByTagName(s)[0];"
          ;                           "if (d.getElementById(id)) {return;} js = d.createElement(s); "
          ;                           "js.id = id;     js.src = \"//connect.facebook.net/en_US/sdk.js\";"
          ;                           "fjs.parentNode.insertBefore(js, fjs);   }"
          ;                           "(document, 'script', 'facebook-jssdk'));"])
          (dom/script {:src  "https://cdn.jsdelivr.net/hls.js/latest/hls.js"
                       :type common/text-javascript})
          (dom/script {:src  "https://cdn.plyr.io/1.8.2/plyr.js"
                       :type common/text-javascript})
          (dom/script {:src  (common/budget-js-path)
                       :type common/text-javascript})

          (when-not (= cljs-build-id "devcards")
            (common/inline-javascript ["env.web.main.runsulo()"])))))))
