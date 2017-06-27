(ns eponai.common.auth
  (:require [eponai.common :as c]
            [eponai.common.database :as db]
            [taoensso.timbre :as timbre :refer [debug]])
  #?(:clj
     (:import [clojure.lang ExceptionInfo])))

(defprotocol IAuthResponder
  (-redirect [this path])
  (-prompt-login [this anything] "Propt user a login. The anything parameter can be used depending on use case.")
  (-unauthorize [this]))

;; TODO: Do this with spec instead
;(defn- get-id [params k]
;  (if-some [ret (or (get-in params [k :db/id])
;                    ;; Add -id to the key k and parse it to a number
;                    (-> (get params (keyword nil (str (name k) "-id")))
;                        (c/parse-long-safe)))]
;    ret
;    (throw (ex-info (str "Unable to get id for key: " k) {:type   :missing-id
;                                                          :params params
;                                                          :id-key k}))))

(defmulti auth-role-query (fn [role auth params] role))
(defmethod auth-role-query ::any-user
  [_ auth _]
  {:where   '[[?user :user/email ?email]]
   :symbols {'?email (:email auth)}})

(defmethod auth-role-query ::exact-user
  [_ _ params]
  (if-let [user-id (or (:user-id params)
                       (get-in params [:user :db/id]))]
    {:symbols {'?user user-id}}
    (throw (ex-info "Unable to get id" {:type      :missing-id
                                        :auth-role ::exact-user
                                        :params    params}))))

(defmethod auth-role-query ::store-owner
  [_ _ params]
  (if-let [store-id (or (:store-id params)
                        (get-in params [:store :db/id]))]
    (do
      (assert (number? store-id)
              (str ":store-id was not a number, was: " store-id " params: " params))
      {:where   '[[?store :store/owners ?owner]
                  [?owner :store.owner/user ?user]]
       :symbols {'?store store-id}})
    (throw (ex-info "Unable to get id" {:type      :missing-id
                                        :auth-role ::store-owner
                                        :params    params}))))

(defmethod auth-role-query ::any-store-owner
  [_ _ _]
  {:where '[[?owner :store.owner/user ?user]
            [?store :store/owners ?owner]]})

(defn auth-query [roles {:keys [email] :as auth} params]
  (let [roles (cond-> roles
                      (keyword? roles) (hash-set))]
    (when (and (seq roles) (string? email))
      (let [query-by-role (into {}
                                (map (juxt identity #(auth-role-query % auth params)))
                                (seq roles))
            query (reduce db/merge-query
                          (or (get query-by-role ::any-user)
                              (auth-role-query ::any-user auth params))
                          (vals (dissoc query-by-role ::any-user)))]
        (assoc query :find '[?user .])))))

(defn is-public-role? [roles]
  (boolean
    (cond
      (keyword? roles)
      (= ::public roles)
      (map? roles)
      (::public roles)
      (coll? roles)
      (::public (set roles)))))

(defn authed-user-for-params [db roles auth params]
  (try
    (when-let [query (auth-query roles auth params)]
      (db/find-with db query))
    (catch #?@(:clj  [ExceptionInfo e]
               :cljs [:default e])
           (if (= :missing-id (:type (ex-data e)))
             nil
             (throw e)))))

(defn authed-for-roles? [db roles auth params]
  (boolean
    (or (is-public-role? roles)
        (authed-user-for-params db roles auth params))))