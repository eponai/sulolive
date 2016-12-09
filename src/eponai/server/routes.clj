(ns eponai.server.routes
  (:require
    [cemerick.friend :as friend]
    [buddy.auth.accessrules :refer [restrict]]
    [buddy.auth.backends :refer [http-basic]]
    [eponai.server.auth.credentials :as a]
    [clojure.string :as clj.string]
    [compojure.core :refer :all]
    [compojure.route :as route]
    [eponai.server.middleware :as m]
    [ring.util.response :as r]
    [eponai.common.parser.util :as parser.util]
    [eponai.server.parser.response :as parser.resp]
    [taoensso.timbre :refer [debug error trace warn]]
    [eponai.server.api :as api]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.facebook :as fb]
    [eponai.server.ui :as server.ui]
    [eponai.common.parser :as parser]
    [buddy.auth :refer [authenticated? throw-unauthorized]]
    [eponai.client.auth :as auth]
    [om.next :as om]))

(defn html [& path]
  (-> (clj.string/join "/" path)
      (r/resource-response {:root "public"})
      (r/content-type "text/html")))

(defn release? [request]
  (= "release" (::m/cljs-build-id request)))

(declare handle-parser-request)

(defn request->props [request]
  {:release?                       (release? request)
   :params                         (:params request)
   ::server.ui/component->props-fn (fn [component]
                                     (-> request
                                         (assoc :body {:query (om/get-query component)})
                                         (handle-parser-request)))})

;----------API Routes

(defn handle-parser-request
  [{:keys [::m/conn ::m/parser ::stripe/stripe-fn ::playground-auth body ::fb/facebook-token-validator] :as request}]
  (debug "Handling parser request with body:" body)
  (parser
    {:eponai.common.parser/read-basis-t (:eponai.common.parser/read-basis-t body)
     :state                             conn
     :auth                              (friend/current-authentication request)
     :stripe-fn                         stripe-fn
     :fb-validate-fn                    facebook-token-validator
     :params                            (:params request)}
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
  admin-routes
  (GET "/" request (server.ui/index-html (request->props request)))
  (GET "/store/:store-id" request (server.ui/store-html (request->props request)))
  (GET "/goods/:product-id" r (server.ui/product-html (request->props r)))
  (GET "/goods" request (server.ui/goods-html (request->props request)))
  (GET "/checkout" request (server.ui/checkout-html (request->props request))))

(defroutes
  site-routes
  (POST "/api" request (r/response (call-parser request)))
  ;(POST "/api" request
  ;  (restrict
  ;    #(r/response (call-parser %))
  ;    {:handler auth/is-user?
  ;     :on-error (fn [& _]
  ;                 (debug "Unauthorized api request")
  ;                 (r/response "You fucked up"))})
  ;  )
  (context "/" [:as request]
    (restrict admin-routes
              {:handler  authenticated?
               :on-error (fn [a b]
                           (debug "Erro request: " a)
                           (debug "Erro: " b)
                           {:status  401
                            :headers {"Content-Type"     "text/plain"
                                      "WWW-Authenticate" (format "Basic realm=\"%s\"" "Demo")}})})
    )
  (route/resources "/")
  (route/not-found "Not found"))