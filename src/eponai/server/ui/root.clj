(ns eponai.server.ui.root
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.server.ui.common :as common]
    [taoensso.timbre :refer [debug]]
    [clojure.data.json :as json]
    [environ.core :as env]
    [eponai.common.shared :as shared]
    [eponai.common.database :as db]
    [eponai.web.seo :as web.seo]))

(defn quote-string [s]
  (str "'" s "'"))

(defui Root
  Object
  (render [this]
    (let [{:keys  [release? route auth cljs-build-id system random-seed]
           ::keys [app-html reconciler]
           :as    props} (om/props this)]
      (debug "app-html: " app-html)
      (om/get-reconciler this)
      (dom/html
        {:lang "en"}
        (common/head {:route         route
                      :release?      release?
                      :cljs-build-id cljs-build-id
                      :random-seed   random-seed
                      :system system
                      :reconciler    reconciler
                      :route-map     (select-keys props [:route
                                                         :route-params
                                                         :query-params])})
        (dom/body
          nil

          (when (seq (env/env :firebase-api-key))
            [(dom/script {:src  "https://www.gstatic.com/firebasejs/4.1.3/firebase.js"
                          :type common/text-javascript})
             (common/inline-javascript
               ["var config = {"
                " apiKey: " (quote-string (env/env :firebase-api-key))
                ", authDomain: " (quote-string (env/env :firebase-auth-domain))
                ", projectId: " (quote-string (env/env :firebase-project-id))
                ", databaseURL: " (quote-string (env/env :firebase-database-url))
                ", storageBucket: " (quote-string (env/env :firebase-storage-bucket))
                ", messagingSenderId: " (quote-string (env/env :firebase-messaging-sender-id))
                "};"
                "firebase.initializeApp(config);"])])

          ;(dom/script {:src "/lib/firebase/firebase-app.js"
          ;             :type common/text-javascript})
          ;(dom/script {:src "/lib/firebase/firebase-messaging.js"
          ;             :type common/text-javascript})
          (dom/div {:height "100%" :id router/dom-app-id}
            app-html)

          (dom/script {:src  "https://cdn.auth0.com/js/auth0/8.7/auth0.min.js"
                       :type common/text-javascript})



          ;<script src="//www.powr.io/powr.js" external-type="html"></script>

          (dom/script {:src "//lightwidget.com/widgets/lightwidget.js"
                       :type common/text-javascript})
          (dom/script {:src  (common/budget-js-path)
                       :type common/text-javascript})

          (when (some? (:user-id auth))
            (common/inline-javascript [(str "mixpanel.identify(\"" (:user-id auth) "\");")
                                       (str "mixpanel.people.set_once(" (json/write-str {:$email (:email auth) :$last_name (:email auth)}) ");")]))

          ;; Powr can be loaded last, because it just hooks in to a div that we've specified.
          (dom/script {:src           "//www.powr.io/powr.js"
                       :external-type "html"})

          (cond
            (some? (env/env :sulo-demo))
            (common/inline-javascript ["env.web.main.rundemo()"])

            (= cljs-build-id "release")
            (if release?
              (common/inline-javascript ["env.web.main.runsulo()"])
              (common/inline-javascript ["env.web.main.run_fake_sulo()"]))

            (= cljs-build-id "devcards")
            nil

            :else
            (common/inline-javascript ["env.web.main.runsulo()"]))))
      )))

(def ->Root (om/factory Root))