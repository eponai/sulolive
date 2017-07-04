(ns eponai.server.routes
  (:require
    [eponai.server.api :as api]
    [eponai.server.auth :as auth]
    [eponai.common.auth :as common.auth]
    [clojure.string :as clj.string]
    [compojure.core :refer :all]
    [compojure.route :as route]
    [eponai.server.middleware :as m]
    [ring.util.response :as r]
    [ring.util.request :as ring.request]
    [bidi.bidi :as bidi]
    [bidi.ring :as bidi.ring]
    [eponai.common.routes :as common.routes]
    [eponai.common.parser :as parser]
    [eponai.common.parser.util :as parser.util]
    [eponai.server.parser.response :as parser.resp]
    [taoensso.timbre :refer [debug error trace warn]]
    [eponai.server.ui :as server.ui]
    [om.next :as om]
    [eponai.server.external.stripe.webhooks :as stripe-webhooks]
    [eponai.server.websocket :as websocket]
    [eponai.server.ui.root :as root]
    [eponai.server.social :as social]
    [eponai.common.routes :as routes]
    [eponai.common.database :as db]
    [eponai.server.log :as log]
    [eponai.common :as c]
    [eponai.common.photos :as photos]
    [eponai.server.external.email.templates :as templates]
    [eponai.common.format.date :as date]
    [eponai.common.location :as location]
    [cemerick.url :as url]))

(defn html [& path]
  (-> (clj.string/join "/" path)
      (r/resource-response {:root "public"})
      (r/content-type "text/html")))

(defn release? [request]
  (true? (::m/in-production? request)))

(declare handle-parser-request)

(defn request->auth [{conn ::m/conn auth :identity}]
  (debug "Request->Auth " auth)
  (let [auth-user (when-some [user-email (:email auth)]
                    (db/lookup-entity (db/db conn) [:user/email user-email]))]
    (assoc auth :user-id (:db/id auth-user))))

;; Inspired by
;; https://stackoverflow.com/a/30022208
(defn- client-ip [request]
  (if-let [ips (get-in request [:headers "x-forwarded-for"])]
    (-> ips (clojure.string/split #",") first)
    (:remote-addr request)))

(defn- context-logger
  ([request route-map]
   (context-logger request
                   route-map
                   (get-in (request->auth request) [:user-id])))
  ([{::m/keys [logger] :as request} route-map user-id]
   {:pre [(or (nil? user-id) (number? user-id))]}
   (let [start (System/currentTimeMillis)
         route (select-keys route-map [:route :route-params :query-params])
         ip (client-ip request)]
     (cond-> (log/with logger #(cond-> (assoc % :context-start start)
                                       (some? ip)
                                       (assoc :client-ip ip)))
             (some? user-id)
             (log/with #(assoc % :user-id user-id))
             (seq route)
             ;; Merging what ever is in the log message in to the route because we don't want to
             ;; overwrite what's already being logged. Route data can be different from what's in
             ;; the current request.
             (log/with #(merge route %))))))

(defn request->props [request]
  ;(debug "Got request: " (into {} request))
  (let [state (::m/conn request)
        route (:handler request)
        system (::m/system request)
        route-params (merge (:params request)
                            (:route-params request))
        sharing-objects (social/share-objects {:route-params route-params
                                               :state        state
                                               :route        route
                                               :system       system})

        props {:empty-datascript-db (::m/empty-datascript-db request)
               :state               state
               :system              system
               :release?            (release? request)
               :cljs-build-id       (::m/cljs-build-id request)
               :route               route
               :route-params        (dissoc route-params :*)
               ;; Remove query-params that exactly equal route-params, because of how
               ;; compojure... does.. stuff?
               :query-params        (into {}
                                          (remove #(contains? routes/only-route-param-keys (key %)))
                                          (dissoc (:params request) :*))
               :auth                (request->auth request)
               :locations           (auth/requested-location request)
               :social-sharing      sharing-objects}
        logger (context-logger request
                               (select-keys props [:route :route-params :query-params])
                               (get-in props [:auth :user-id]))]
    (assoc props :logger logger)))


;---------- Auth handlers



;----------API Routes

(defn handle-parser-request
  [{:keys [body cookies] ::m/keys [conn parser-fn system] :as request} read-basis-t-graph]
  (debug "Handling parser request with query:" (:query body))
  (debug "Handling parser request with cookies:" cookies)
  (let [{:keys [user-id] :as auth} (request->auth request)
        route-map (:route-map body)]
    ((parser-fn)
      {::parser/read-basis-t-graph  (some-> read-basis-t-graph (atom))
       ::parser/chat-update-basis-t (::parser/chat-update-basis-t body)
       ::parser/auth-responder      (::parser/auth-responder request)
       :state                       conn
       :auth                        auth
       :params                      (:params request)
       :system                      system
       :locations                   (auth/requested-location request)
       :logger                      (context-logger request route-map user-id)}
      (:query body))))

(defn trace-parser-response-handlers
  "Wrapper with logging for parser.response/response-handler."
  [env key params]
  (trace "handling parser response for key:" key "value:" params)
  (parser.resp/response-handler env key params))

(def handle-parser-response
  "Will call response-handler for each key value in the parsed result."
  (-> (parser.util/post-process-parse trace-parser-response-handlers [])))

(defn call-parser [{:keys [::m/conn] :as request}]
  (let [clients-auth (get-in request [:body :auth :email])
        cookie-auth (get-in request [:identity :email])
        read-basis-t-graph (some-> (::parser/read-basis-t (:body request))
                                   (parser.util/graph-read-at-basis-t true))
        has-queried-auth? (some-> read-basis-t-graph (parser.util/has-basis-t? :query/auth))]
    (if (and has-queried-auth? (not= clients-auth cookie-auth))
      ;; If we've queried auth and the client and cookie doesn't agree who's authed, logout.
      ;; TODO: Do this force-logout in a different way?
      ;;       We do this because the client's auth and the cookie's auth may
      ;;       not agree on who's logged in. We may want to prompt a "continue as"
      ;;       screen, instead of forcing the logout.
      (do
        (debug "Has queried auth and client's auth doesn't match cookie's auth.")
        (debug "clients-auth: " clients-auth " cookie-auth: " cookie-auth)
        (trace "request: " (into {} (remove (comp #{::m/empty-datascript-db
                                                    ::m/system}
                                                  key))
                                 request))
        {:auth {:logout true}})
      (let [auth-responder (parser/stateful-auth-responder)
            ret (handle-parser-request (assoc request ::parser/auth-responder auth-responder)
                                       read-basis-t-graph)
            basis-t-graph (some-> ret (meta) (::parser/read-basis-t-graph) (deref))
            ret (->> ret
                     (handle-parser-response (assoc request :state conn))
                     (parser.resp/remove-mutation-tx-reports))
            auth-map (->> {:redirects    (common.auth/-redirect auth-responder nil)
                           :prompt-login (common.auth/-prompt-login auth-responder nil)
                           :unauthorized (common.auth/-unauthorize auth-responder)}
                          (into {} (remove #(nil? (val %)))))]
        (cond-> {:result ret
                 :meta   {::parser/read-basis-t basis-t-graph}}
                (seq auth-map)
                (assoc :auth auth-map))))))

(defn bidi-route-handler [route]
  ;; Currently all routes render the same way.
  ;; Enter route specific stuff here.
  (-> (fn [{:keys [route-params ::m/conn] :as request}]
        (let [resp (-> (r/response (server.ui/render-site (request->props (assoc request :handler route))))
                       (r/content-type "text/html")
                       (r/charset "UTF-8"))
              new-local (auth/requested-location request)
              old-local (auth/cookie-locality request)]

          (if (not= (:sulo-locality/path new-local)
                    (:sulo-locality/path old-local))
            (r/set-cookie resp
                          location/locality-cookie-name
                          (c/write-transit new-local)
                          {:path "/"})
            resp)))
      (auth/restrict (auth/bidi-location-redirect route))
      (auth/restrict (auth/bidi-route-restrictions route))))

(defroutes
  member-routes
  ;; Hooks in bidi routes with compojure.
  ;; TODO: Cache the handlers for each route.
  (GET "*" request (bidi.ring/make-handler common.routes/routes bidi-route-handler)))

(defroutes
  site-routes
  (GET "/aws" request (api/aws-s3-sign request))
  (POST "/api" request
    (r/response (call-parser request)))
  (POST "/api/chat" request
    (r/response (call-parser request)))

  (GET "/auth" request (auth/authenticate
                         (assoc request ::m/logger (context-logger request {:route :auth}))))
  (GET "/login" request (bidi.ring/make-handler common.routes/routes bidi-route-handler)
                        ;(do
                        ;  (debug "/login endpoint")
                        ;  (if (some #(#{:code :token :access_token} (key %)) (:params request))
                        ;    (auth/authenticate
                        ;      (assoc request ::m/logger (context-logger request {:route :login})))
                        ;    (bidi.ring/make-handler common.routes/routes bidi-route-handler)))
                        )

  (GET "/logout" request
    (let [logger (context-logger request {:route :logout})
          redirect (or (get-in request [:params :redirect])
                       (routes/path :landing-page))]
      (log/info! logger ::user-logout {:redirect redirect})
      (-> (auth/redirect (assoc request ::m/logger logger) redirect)
          (auth/remove-auth-cookie))))

  (GET "/devcards" request
    (when-not (::m/in-production? request)
      (server.ui/render-to-str root/Root {:route         :devcards
                                          :cljs-build-id "devcards"
                                          :release?      false})))

  ;; Websockets
  (GET "/ws/chat" {::m/keys [system] :as request}
    (websocket/handle-get-request (:system/chat-websocket system) request))
  (POST "/ws/chat" {::m/keys [system] :as request}
    (websocket/handler-post-request (:system/chat-websocket system) request))

  ;; Webhooks
  (POST "/stripe/connected" request (r/response (stripe-webhooks/handle-connected-webhook
                                                  {:state  (::m/conn request)
                                                   :system (::m/system request)}
                                                  (:body request))))
  (POST "/stripe" request (r/response (stripe-webhooks/handle-account-webhook
                                        {:state  (::m/conn request)
                                         :system (::m/system request)}
                                        (:body request))))

  (context "/" [:as request]
    member-routes

    ;(if (release? request)
    ;  (auth/restrict member-routes (auth/member-restrict-opts))
    ;  member-routes)
    ))