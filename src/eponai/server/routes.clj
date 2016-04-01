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
            [eponai.server.api :as api]
            [eponai.server.external.stripe :as stripe]
            [clojure.data.json :as json]
            [eponai.server.email :as email]))

(defn html [& path]
  (-> (clj.string/join "/" path)
      (r/resource-response {:root "public"})
      (r/content-type "text/html")))

(defroutes
  app-routes
  (GET "/*" request (html (::m/cljs-build-id request) "app.html")))

(defroutes
  site-routes
  (GET "/" [:as request]
    (if (friend/current-authentication request)
      (r/redirect "/app")
      (html "index.html")))
  (ANY "/stripe" {:keys [::m/conn body]}
    (try
      (r/response (stripe/webhook conn body {:send-email-fn #(email/send-email %1 nil %2)}))
      (catch Exception e
        (r/response {}))))

  (context "/app" _
    (friend/wrap-authorize app-routes #{::a/user}))

  (GET "/verify/:uuid" [uuid]
    (r/redirect (str "/api/login/email?uuid=" uuid)))

  (GET "/signup" request
    (html (::m/cljs-build-id request) "signup.html"))

  (GET "/devcards" []
    (html "devcards" "app.html"))

  (POST
    "/newsletter/subscribe" {params :params
                             conn ::m/conn}

    (try
      (api/newsletter-subscribe conn (:email params))
      (r/response (json/write-str {:message "Thank you, we'll let you know the second we launch!"}))
      (catch Exception e
        (error "Exception when subscribing user:" (:email params) "exception:" e)
        (r/status (r/response (json/write-str {:message "Oops, something went wrong. Please try again in a little while."})) 500))))

  (route/resources "/")
  (route/not-found "Not found"))

;----------API Routes

(defn handle-parser-request
  [{:keys [::m/conn ::m/parser ::m/make-parser-error-fn ::m/stripe-fn body] :as request}]
  (debug "Handling parser request with body:" (into [] body))
  (parser
    {:state           conn
     :auth            (friend/current-authentication request)
     :parser-error-fn (when make-parser-error-fn
                        (make-parser-error-fn request))
     :stripe-fn       stripe-fn}
    body))

(defn trace-parser-response-handlers
  "Wrapper with logging for parser.response/response-handler."
  [env key params]
  (trace "handling parser response for key:" key "value:" params)
  (parser.resp/response-handler env key params))

(defn remove-mutation-tx-reports
  "Removes :db-after, :db-before and :tx-data from our
  mutations' return values."
  [response]
  (reduce-kv (fn [m k _]
               (if-not (symbol? k)
                 m
                 (update-in m [k :result] dissoc :db-after :db-before :tx-data :tempids)))
             response
             response))

(def handle-parser-response
  "Will call response-handler for each key value in the parsed result."
  (parser.util/post-process-parse trace-parser-response-handlers []))

(defroutes
  user-routes
  (POST "/" {:keys [::m/conn] :as request}
    (r/response
      (->> (handle-parser-request request)
           (handle-parser-response (assoc request :state conn))
           (remove-mutation-tx-reports)))))

(defroutes
  api-routes
  (context "/api" []

    (POST "/" request
      (let [ret
            (->> (handle-parser-request request)
                 (handle-parser-response request))]
        (r/response ret)))

    ; Requires user login
    (context "/user" _
      (friend/wrap-authorize user-routes #{::a/user}))

    (friend/logout (ANY "/logout" [] (r/redirect "/")))))
