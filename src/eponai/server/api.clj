(ns eponai.server.api
  (:require [cemerick.friend :as friend]
    ;[clojure.core.async :refer [go >!]]
            [compojure.core :refer :all]
            [datomic.api :as d]
            [eponai.server.auth.credentials :as a]
            [eponai.server.datomic.transact :as t]
            [eponai.server.datomic.pull :as p]
            [eponai.server.http :as h]
            [eponai.server.middleware :as m]
            [ring.util.response :as r]))

; Actions

(defn signup
  "Create a new user and transact into datomic."
  [{:keys [request-method params ::m/email-chan ::m/conn]
    :as request}]
  (if (and (= request-method :post)
           (not (p/user (d/db conn) (params :username))))
    (let [tx (t/new-user conn (a/add-bcrypt params :password))]
      (when email-chan
        ;(go (>! email-chan [(:db-after tx) (params :username)]))
        )
      tx)
    (throw (ex-info "Cannot create new signup."
                    {:cause   ::signup-error
                     :status  ::h/unathorized
                     :data    {:username       (params :username)
                               :request-method request-method}
                     :message "Cannot sign up."}))))


(defn verify [conn uuid]
  (let [db (d/db conn)
        verification (p/verification db uuid)]
    (if (= (:db/id (verification :verification/status))
           (d/entid db :verification.status/pending))
      (t/add conn (:db/id verification) :verification/status :verification.status/verified)
      (throw (ex-info "Trying to activate invalid verification."
                      {:cause   ::verification-error
                       :status  ::h/unathorized
                       :data    {:uuid uuid}
                       :message "The verification link is no longer valid."})))))

(defn post-currencies [conn curs]
  (t/currencies conn curs))

(defn post-currency-info [conn cur-infos]
  (t/currency-infos conn cur-infos))

(defn post-currency-rates [conn rates-fn dates]
  (let [unconverted (clojure.set/difference (set dates)
                                            (p/converted-dates (d/db conn) dates))]
    (when (some identity unconverted)
      (t/currency-rates conn (map rates-fn (filter identity dates))))))

(defn post-user-data
  "Post new transactions for the user in the session. If there's no currency rates
  for the date of the transactions, they will be fetched from OER."
  [conn request]
  (let [budget (p/budget (d/db conn) (:username (friend/current-authentication request)))
        user-data (map #(assoc % :transaction/budget (:budget/uuid budget))
                       (:body request))]
    ;(go (>! currency-chan (map :transaction/date user-data)))
    (t/user-txs conn user-data)))

(defn send-email-verification [email-fn [db email]]
  (when-let [verification (first (p/verifications db (p/user db email) :user/email :verification.status/pending))]
    (email-fn email (verification :verification/uuid))))

;----------Routes

(defroutes
  user-routes
  (POST "/" {:keys [body ::m/conn ::m/currency-chan ::m/parser]
             :as req}
    (r/response
      (let [ret (parser
                  {:state         conn
                   :auth          (friend/current-authentication req)
                   :currency-chan currency-chan}
                  body)]
        (reduce-kv (fn [old k {:keys [om.next/error] :as v}]
                     (if error
                       (do (prn "Om next exception for key: " k)
                           (.printStackTrace error)
                           (assoc-in old [k :om.next/error] (str "got error, lol")))
                       old))
                   ret ret)))))

(defroutes
  api-routes
  (context "/api" []
    (POST "/signup" request
      (r/response (signup request)))
    ; Requires user login
    (context "/user" _
      (friend/wrap-authorize user-routes #{::a/user}))
    (GET "/verify/:uuid" [uuid :as request]
      (do
        (verify (::m/conn request) uuid)
        (r/response {:message "Your email is verified, you can now login."})))
    (friend/logout (ANY "/logout" [] (r/redirect "/")))))