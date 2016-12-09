(ns eponai.server.auth.auth0
  (:require
    [clj-http.client :as client]
    [clojure.data.json :as json]
    [cemerick.url :as url]))

(defn token-info [token]
  ;(json/read-str
  ;  (:body (client/post "https://sulo.auth0.com/tokeninfo" {:form-params {:id_token token} :content-type :json}))
  ;  :key-fn
  ;  keyword)
  {:user_id "userid" :email "testemail"})


