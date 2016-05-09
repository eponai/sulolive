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
            [taoensso.timbre :refer [debug error trace warn]]
            [eponai.server.api :as api]
            [eponai.server.external.stripe :as stripe]
            [clojure.data.json :as json]
            [eponai.server.email :as email]
            [eponai.common.parser :as parser])
  (:import [clojure.lang ExceptionInfo]))

(defn html [& path]
  (-> (clj.string/join "/" path)
      (r/resource-response {:root "public"})
      (r/content-type "text/html")))

(defroutes
  app-routes
  (GET "/*" request (html (::m/cljs-build-id request) "app.html")))

(defroutes
  playground-routes
  (GET "/*" request (html (::m/cljs-build-id request) "playground.html")))

(defroutes
  site-routes
  (GET "/" [:as request]
    (if (friend/current-authentication request)
      (r/redirect "/app")
      (html "index.html")))
  (ANY "/stripe" {:keys [::m/conn body]}
    (try
      (let [result (stripe/webhook conn body {::email/send-payment-reminder-fn email/send-payment-reminder-email})]
        (debug "Stripe webhook handled with result: " result)
        (r/response {:status :SUCCESS
                     :message "ok"}))
      (catch ExceptionInfo e
        (error e)
        (r/response {:status :ERROR
                     :message (.getMessage e)
                     :ex-data (ex-data e)}))
      (catch Exception e
        (error e))))

  (context "/app" _
    (friend/wrap-authorize app-routes #{::a/user}))

  (context "/play" _ playground-routes)

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
  [{:keys [::m/conn ::m/parser ::m/make-parser-error-fn ::m/stripe-fn ::playground-auth body] :as request}]
  (debug "Handling parser request with body:" body)
  (parser
    {:eponai.common.parser/read-basis-t (:eponai.common.parser/read-basis-t body)
     :state                             conn
     :auth                              (or playground-auth (friend/current-authentication request))
     :parser-error-fn                   (when make-parser-error-fn
                                          (make-parser-error-fn request))
     :stripe-fn                         stripe-fn}
    (:query body)))

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

(defn meta-from-keys [x]
  {:post [(or (nil? %) (map? %))]}
  (cond
    (map? x)
    (reduce-kv (fn [m k v]
                 (if (keyword? k)
                   (merge-with merge
                               m
                               (meta v)
                               (meta-from-keys v))
                   m))
               {}
               x)
    (sequential? x)
    (apply merge (mapv meta-from-keys x))
    :else (reduce-kv (fn [m k v]
                       (cond-> m (some? v) (assoc k v)))
                     nil
                     (meta x))))

(def handle-parser-response
  "Will call response-handler for each key value in the parsed result."
  (-> (parser.util/post-process-parse trace-parser-response-handlers [])))

(defn call-parser [{:keys [::m/conn] :as request}]
  (let [ret (handle-parser-request request)
        m (meta-from-keys ret)
        ret (->> ret
                 (handle-parser-response (assoc request :state conn))
                 (remove-mutation-tx-reports))]
    {:result ret
     :meta m}))

(defroutes
  user-routes
  (POST "/" request
    (r/response (call-parser request))))

(defroutes
  api-routes
  (context "/api" []

    (POST "/" request
      (r/response (call-parser request)))

    ;; TODO: We need a test which fails if a request mutates the db.
    (POST "/playground" request
      (let [user-uuid-fn (::m/playground-user-uuid-fn request)
            user-uuid (if (fn? user-uuid-fn)
                        (user-uuid-fn)
                        (warn "user-uuid-fn was not a function. Was: " user-uuid-fn))]
        (if (some? user-uuid)
          (r/response (call-parser (-> request
                                       (assoc ::playground-auth {:username user-uuid})
                                       (update ::m/parser (fn [parser] (-> parser
                                                                           parser/parse-without-mutations
                                                                           parser/parser-require-auth))))))
          (throw (ex-info "No playground user-uuid with request. Will not call parser."
                          {:request          request
                           :keys-of-interest [::m/playground-user-uuid-fn]})))))

    ; Requires user login
    (context "/user" _
      (friend/wrap-authorize user-routes #{::a/user}))

    (friend/logout (ANY "/logout" [] (r/redirect "/")))))
