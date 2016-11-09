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
            [eponai.common.parser :as parser]
            [eponai.server.auth.workflows :as w]
            [om.next :as om])
  (:import [clojure.lang ExceptionInfo]))

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
    (if (friend/authorized? #{::a/user} request)
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

  (context "/play" _
    (GET "/*" request (html (::m/cljs-build-id request) "playground.html")))

  (GET "/activate" request
    (friend/authorize #{::a/user-inactive} (html (::m/cljs-build-id request) "signup.html")))

  (GET "/verify/:uuid" [uuid]
    (r/redirect (str "/api/login/email?uuid=" uuid)))

  (GET "/signup" request
    (cond (friend/authorized? #{::a/user} request)
          (r/redirect "/app")

          (friend/authorized? #{::a/user-inactive} request)
          (r/redirect "/activate")

          :else
          (html (::m/cljs-build-id request) "signup.html")))

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
  [{:keys [::m/conn ::m/parser ::stripe/stripe-fn ::playground-auth body] :as request}]
  (debug "Handling parser request with body:" body)
  (parser
    {:eponai.common.parser/read-basis-t (:eponai.common.parser/read-basis-t body)
     :state                             conn
     :auth                              (or playground-auth (friend/current-authentication request))
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
  (letfn [(deep-merge-fn [a b]
            (if (map? a)
              (merge-with deep-merge-fn a b)
              b))]
    (cond
     (map? x)
     (reduce-kv (fn [m k v]
                  (if (keyword? k)
                    (merge-with deep-merge-fn
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
                      (meta x)))))

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

(defn playground-handler [request parser]
  (let [user-uuid-fn (::m/playground-user-uuid-fn request)
        user-uuid (if (fn? user-uuid-fn)
                    (user-uuid-fn)
                    (warn "user-uuid-fn was not a function. Was: " user-uuid-fn))]
    (if (some? user-uuid)
      (r/response (call-parser (-> request
                                   (assoc ::playground-auth {:username user-uuid})
                                   (assoc ::m/parser parser))))
      (let [msg "No playground user-uuid with request. Will not call parser."]
        (throw (ex-info msg
                        {:message          msg
                         :keys-of-interest [::m/playground-user-uuid-fn]}))))))

(defroutes
  api-routes
  (context "/api" []

    (POST "/" request
      (debug "Got request:")
      (debug request)
      (r/response (call-parser request)))

    ;; TODO: We need a test which fails if a request mutates the db.
    (context "/playground" []

      ;; This is an endpoint where we can only subscribe to our newsletter from the playground.
      (POST "/subscribe" request
        (playground-handler
          request
          (om/parser {:read   (constantly nil)
                      :mutate (fn [_ k p]
                                (if-not (= k 'playground/subscribe)
                                  (throw (ex-info (str "Mutation not allowed: " k) {:mutation k :params p}))
                                  (do (assert (string? (:email p)))
                                      (api/newsletter-subscribe (::m/conn request) (:email p)))))})))
      (POST "/" request
        (playground-handler
          request
          (-> (parser/server-parser (parser/server-parser-state {:mutate (constantly nil)}))
              ;; Remove mutations from query even though there
              ;; isn't any mutate function, just in case.
              parser/parse-without-mutations
              parser/parser-require-auth))))

    ; Requires user login
    (context "/user" _
      (friend/wrap-authorize user-routes #{::a/user}))

    (friend/logout (ANY "/logout" [] (r/redirect "/")))))
