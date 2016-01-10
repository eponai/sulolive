(ns eponai.server.site
  (:require [cemerick.friend :as friend]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [eponai.server.auth.credentials :as a]
            [eponai.server.middleware :as m]
            [ring.util.response :as r]
            [eponai.common.parser.util :as parser.util]
            [eponai.server.parser.response :as parser.resp]))

(defn html [& path]
  (-> (apply str path)
      (r/resource-response {:root "public"})
      (r/content-type "text/html")))

(defroutes
  site-routes
  (GET "/" [:as request]
    (if (friend/current-authentication)
      (html (str "/" (::m/cljs-build-id request) "/budget.html"))
      (html "index.html")))

  (GET "/budget" request
    (friend/authorize #{::a/user}
                      (r/redirect (str "/" (::m/cljs-build-id request) "/budget.html"))))

  (GET "/verify/:uuid" [uuid]
    (r/redirect (str "/api/login/email?uuid=" uuid)))

  (GET "/signup" {:keys [query-string] :as request}
    (r/redirect
      (cond-> (str "/" (::m/cljs-build-id request) "/signup.html")
              query-string
              (str "?" query-string))))

  (GET "/devcards" []
    (html "devcards/budget.html"))

  (route/resources "/")
  (route/not-found "Not found"))

;----------API Routes

(defn handle-parser-request
  [{:keys [::m/conn ::m/parser body] :as request}]
  (parser
    {:state conn
     :auth (friend/current-authentication request)}
    body))

(def handle-parser-response
  "Will call response-handler for each key value in the parsed result."
  (parser.util/post-process-parse parser.resp/response-handler []))

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

    ; Requires user login
    (context "/user" _
      (friend/wrap-authorize user-routes #{::a/user}))

    (POST "/verify" request
      (r/response
        (handle-parser-request request)))

    (friend/logout (ANY "/logout" [] (r/redirect "/")))))
