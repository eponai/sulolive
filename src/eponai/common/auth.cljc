(ns eponai.common.auth
  (:require [eponai.common :as c]
            [eponai.common.database :as db]
            [taoensso.timbre :refer [debug]])
  #?(:clj
     (:import [clojure.lang ExceptionInfo])))

(defprotocol IAuthResponder
  (-redirect [this path])
  (-prompt-login [this])
  (-unauthorize [this]))

;; TODO: Do this with spec instead
(defn- get-id [role params k]
  (if-some [ret (or (c/parse-long-safe (get params role))
                    (get-in params [k :db/id])
                    (->> (keyword nil (str (name k) "-id"))
                         (get params)
                         (c/parse-long-safe)))]
    ret
    (throw (ex-info (str "Unable to get id for key: " k) {:type   :missing-id
                                                          :params params
                                                          :id-key k}))))

(defmulti auth-role-query (fn [role auth params] role))
(defmethod auth-role-query ::any-user
  [_ auth _]
  {:where   '[[?user :user/email ?email]]
   :symbols {'?email (:email auth)}})

(defmethod auth-role-query ::exact-user
  [role _ params]
  {:symbols {'?user (get-id role params :user)}})

(defmethod auth-role-query ::store-owner
  [role _ params]
  {:where   '[[?store :store/owners ?owner]
              [?owner :store.owner/user ?user]]
   :symbols {'?store (get-id role params :store)}})

(defmethod auth-role-query ::exact-store
  [role _ params]
  {:where   '[[?store :store/owners _]]
   :symbols {'?store (get-id role params :store)}})

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
      (debug "query: " query " role: " roles)
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