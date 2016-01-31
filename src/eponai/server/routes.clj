(ns eponai.server.routes
  (:require [cemerick.friend :as friend]
            [clojure.string :as clj.string]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [eponai.server.auth.credentials :as a]
            [eponai.server.middleware :as m]
            [ring.util.response :as r]
            [eponai.common.parser.util :as parser.util]
            [eponai.server.parser.response :as parser.resp]
            [taoensso.timbre :refer [debug error trace]]
            [eponai.server.api :as api]))

(defn html [& path]
  (-> (clj.string/join "/" path)
      (r/resource-response {:root "public"})
      (r/content-type "text/html")))

(defroutes
  app-routes
  (GET "/*" request (html (::m/cljs-build-id request) "budget.html")))

(defroutes
  site-routes
  (GET "/" [:as request]
    (if (friend/current-authentication request)
      (r/redirect "/app")
      (html "index.html")))

  (context "/app" _
    (friend/wrap-authorize app-routes #{::a/user}))

  (GET "/verify/:uuid" [uuid]
    (r/redirect (str "/api/login/email?uuid=" uuid)))

  (GET "/signup" request
    (html (::m/cljs-build-id request) "signup.html"))

  (GET "/devcards" []
    (html "devcards" "budget.html"))

  (route/resources "/")
  (route/not-found "Not found"))

;----------API Routes

(defn handle-parser-request
  [{:keys [::m/conn ::m/parser ::m/make-parser-error-fn body] :as request}]
  (debug "Handling parser request with body:" (into [] body))
  (parser
    {:state           conn
     :auth            (friend/current-authentication request)
     :parser-error-fn (when make-parser-error-fn
                        (make-parser-error-fn request))}
       body))

(defn trace-parser-response-handlers
  "Wrapper with logging for parser.response/response-handler."
  [env key params]
  (debug "handling parser response for key:" key "use logging level :trace to see the value for this key.")
  (trace "full parser response for key:" key "value:" params)
  (parser.resp/response-handler env key params))

(def handle-parser-response
  "Will call response-handler for each key value in the parsed result."
  (parser.util/post-process-parse trace-parser-response-handlers []))

(defroutes
  user-routes
  (POST "/" {:keys [::m/conn] :as request}
    (r/response
      (->> (handle-parser-request request)
           (handle-parser-response (assoc request :state conn))))))

(defroutes
  api-routes
  (context "/api" []

    (POST "/" request
      (let [ret
            (->> (handle-parser-request request)
                 (handle-parser-response request))]
        (r/response ret))
      ;(go (send-email-fn (<! (signup conn params))))
      ;(r/redirect "/sdlogin.html")
      )

    (POST "/charge" {params :params}
      (api/stripe-charge params)
      (r/redirect "/index.html"))

    ; Requires user login
    (context "/user" _
      (friend/wrap-authorize user-routes #{::a/user}))

    (POST "/verify" request
      (r/response
        (handle-parser-request request)))

    (friend/logout (ANY "/logout" [] (r/redirect "/")))))
