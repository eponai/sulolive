(ns eponai.server.routes
  (:require
    [eponai.server.auth :as auth]
    [clojure.string :as clj.string]
    [compojure.core :refer :all]
    [compojure.route :as route]
    [eponai.server.middleware :as m]
    [ring.util.response :as r]
    [eponai.common.parser.util :as parser.util]
    [eponai.server.parser.response :as parser.resp]
    [taoensso.timbre :refer [debug error trace warn]]
    [eponai.server.ui :as server.ui]
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
   :auth                           (:identity request)
   ::server.ui/component->props-fn (fn [component]
                                     (-> request
                                         (assoc :body {:query (om/get-query component)})
                                         (handle-parser-request)))})

;----------API Routes

(defn handle-parser-request
  [{:keys [::m/conn ::m/parser body] :as request}]
  (debug "Handling parser request with body:" body)
  (parser
    {:eponai.common.parser/read-basis-t (:eponai.common.parser/read-basis-t body)
     :state                             conn
     :auth                              (:identity request)
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
  (GET "/auth" request (server.ui/auth-html (merge (request->props request) (auth/auth0 request))))
  (GET "/store/:store-id" request (server.ui/store-html (request->props request)))
  (GET "/goods/:product-id" r (server.ui/product-html (request->props r)))
  (GET "/goods" request (server.ui/goods-html (request->props request)))
  (GET "/checkout" request (server.ui/checkout-html (request->props request))))

(defroutes
  site-routes
  (POST "/api/user" request
    (r/response (call-parser request))
    ;(auth/restrict
    ;  #(r/response (call-parser %))
    ;  (auth/jwt-restrict-opts))
    )
  (POST "/api" request
    (r/response (call-parser request))
    ;(auth/restrict
    ;  #(r/response (call-parser %))
    ;  (auth/jwt-restrict-opts))
    )
  (route/resources "/")
  (context "/" [:as request]
    (if (release? request)
      (auth/restrict admin-routes (auth/http-basic-restrict-opts))
      admin-routes)
    )
  (route/not-found "Not found"))