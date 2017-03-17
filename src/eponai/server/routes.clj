(ns eponai.server.routes
  (:require
    [eponai.server.api :as api]
    [eponai.server.auth :as auth]
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
    [eponai.server.external.stripe :as stripe]
    [eponai.server.websocket :as websocket]
    [eponai.server.ui.root :as root]))

(defn html [& path]
  (-> (clj.string/join "/" path)
      (r/resource-response {:root "public"})
      (r/content-type "text/html")))

(defn release? [request]
  (true? (::m/in-production? request)))

(declare handle-parser-request)

(defn request->props [request]
  {:empty-datascript-db            (::m/empty-datascript-db request)
   :state                          (::m/conn request)
   :system                         (::m/system request)
   :release?                       (release? request)
   :cljs-build-id                  (::m/cljs-build-id request)
   :route-params                   (merge (:route-params request)
                                          (:params request))
   :route                          (:handler request)
   :query-params                   (:params request)
   :auth                           (:identity request)
   ::server.ui/component->props-fn (fn [component]
                                     (-> request
                                         (assoc :body {:query (om/get-query component)})
                                         (handle-parser-request)))})

;----------API Routes

(defn handle-parser-request
  [{:keys [body] ::m/keys [conn parser system] :as request}]
  (debug "Handling parser request with body:" body)
  (debug "SYSTEM PARSER REQUEST: " system)
  (parser
    {::parser/read-basis-t (::parser/read-basis-t body)
     :state                conn
     :auth                 (:identity request)
     :params               (:params request)
     :system               system}
    (:query body)))

(defn trace-parser-response-handlers
  "Wrapper with logging for parser.response/response-handler."
  [env key params]
  (trace "handling parser response for key:" key "value:" params)
  (parser.resp/response-handler env key params))

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
                 (parser.resp/remove-mutation-tx-reports))]
    {:result ret
     :meta   m}))

(defn bidi-route-handler [route]
  ;; Currently all routes render the same way.
  ;; Enter route specific stuff here.
  (fn [request]
    (server.ui/render-site (request->props (assoc request :handler route)))))

(defroutes
  member-routes
  ;; Hooks in bidi routes with compojure.
  (GET "*" _ (bidi.ring/make-handler common.routes/routes bidi-route-handler)))

(defroutes
  site-routes
  (POST "/api/user" request
    (r/response (call-parser request))
    ;(auth/restrict
    ;  #(r/response (call-parser %))
    ;  (auth/jwt-restrict-opts))
    )
  (POST "/api/chat" request
    (r/response (call-parser request)))

  (GET "/aws" request (api/aws-s3-sign request))
  (POST "/api" request
    ;(r/response (call-parser request))
    (auth/restrict
      #(r/response (call-parser %))
      (auth/jwt-restrict-opts)))

  (route/resources "/")
  ;(POST "/stripe/main" request (r/response (stripe/webhook (::m/conn request) (:params request))))
  (GET "/auth" request (auth/authenticate request))

  (GET "/logout" request (auth/logout request))

  (GET "/devcards" request
    (when-not (::m/in-production? request)
      (server.ui/render-to-str root/Root {:route         :devcards
                                          :cljs-build-id "devcards"
                                          :release?      false})))

  (GET "/coming-soon" _ (bidi.ring/make-handler common.routes/routes bidi-route-handler))
  (GET "/sell/coming-soon" _ (bidi.ring/make-handler common.routes/routes bidi-route-handler))

  ;; Websockets
  (GET "/ws/chat" {::m/keys [system] :as request}
    (debug "chat-websocket: " (keys (:system/chat-websocket system)))
    (websocket/handle-get-request (:system/chat-websocket system)
                                  request))
  (POST "/ws/chat" {::m/keys [system] :as request}
    (websocket/handler-post-request (:system/chat-websocket system)
                                    request))

  (context "/" [:as request]
    (cond-> member-routes
            (or (::m/in-production? request))
            (auth/restrict (auth/member-restrict-opts)))
    ;(if (release? request)
    ;  (auth/restrict member-routes (auth/member-restrict-opts))
    ;  member-routes)
    )


  (route/not-found "Not found"))