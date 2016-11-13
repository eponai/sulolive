(ns eponai.server.datomic-dev
  (:require [datomic.api :as d]
            [environ.core :refer [env]]
            [eponai.common.format :as format]
            [eponai.server.import.ods :as ods]
            [clojure.tools.reader.edn :as edn]
            [clojure.java.io :as io]
            [eponai.common.database.transact :as transact]
            [eponai.common.database.functions :as dbfn]
            [taoensso.timbre :refer [debug error info]]
            [eponai.server.datomic.format :as f]
            [clj-time.coerce :as c]
            [clj-time.core :as t])
  (:import (java.util UUID)))

(def currencies {:THB "Thai Baht"
                 :SEK "Swedish Krona"
                 :USD "US Dollar"
                 :JPY "Japanese Yen"
                 :MYR "Malaysian Ringit"
                 :VND "Vietnamese Dollar"
                 :EUR "Euro"
                 :ALL "Albanian Money"
                 :HRK "Croatian Money"
                 :CNY "Chineese Money"})

(defn transactions []
  [{:transaction/title      "lunch"
    :transaction/date       {:date/ymd "2015-10-10"}
    :transaction/amount     "180"
    :transaction/currency   {:currency/code "THB"}
    :transaction/created-at 1
    :transaction/tags       [{:tag/name "thailand"}]
    :transaction/type       :transaction.type/expense}
   {:transaction/title      "coffee"
    :transaction/date       {:date/ymd "2015-10-10"}
    :transaction/amount     "140"
    :transaction/currency   {:currency/code "THB"}
    :transaction/created-at 1
    :transaction/type       :transaction.type/expense}
   {:transaction/title      "dinner"
    :transaction/date       {:date/ymd "2015-10-10"}
    :transaction/amount     "350"
    :transaction/currency   {:currency/code "THB"}
    :transaction/created-at 1
    :transaction/type       :transaction.type/expense}
   {:transaction/title      "market"
    :transaction/date       {:date/ymd "2015-10-11"}
    :transaction/amount     "789"
    :transaction/currency   {:currency/code "THB"}
    :transaction/created-at 1
    :transaction/type       :transaction.type/expense}
   {:transaction/title      "lunch"
    :transaction/date       {:date/ymd "2015-10-11"}
    :transaction/amount     "125"
    :transaction/currency   {:currency/code "THB"}
    :transaction/created-at 1
    :transaction/type       :transaction.type/expense}])

(def test-user-email "test-user@email.com")

(defn create-new-inmemory-db
  ([] (create-new-inmemory-db "test-db"))
  ([db-name]
   (let [uri (str "datomic:mem://" db-name)]
     (d/delete-database uri)
     (d/create-database uri)
     (d/connect uri))))

(defn database-functions-schema []
  [{:db/id    #db/id [:db.part/user]
    :db/ident :db.fn/edit-attr
    :db/fn    (dbfn/dbfn dbfn/edit-attr)}])

(defn list-schema-files []
  (for [i (range)
        :let [schema (io/resource (str "private/datomic/schema/schema-" i ".edn"))]
        :while schema]
    schema))

(defn read-schema-files
  ([] (read-schema-files (list-schema-files)))
  ([schema-files]
   (let [schemas (map #(->> %
                            slurp
                            (edn/read-string {:readers *data-readers*}))
                      schema-files)]
     (conj schemas
           (database-functions-schema)))))

(defn add-verified-user-account [conn email project-uuid]
  (let [{:keys [user] :as account} (f/user-account-map email {:verification/status :verification.status/verified
                                                              :user/status         :user.status/active})
        project (format/project (:db/id user) {:project/name "Project-1"
                                               :project/uuid project-uuid})
        dashboard (format/dashboard (:db/id project))
        stripe-user (format/add-tempid {:stripe/user (:db/id user)
                                        :stripe/customer     "cus_9YcNWTiUBc4Jpm"
                                        :stripe/subscription (format/add-tempid {:stripe.subscription/id         "sub_9YcN9rluT5azTj"
                                                                                 :stripe.subscription/status     :active})})
        ret (transact/transact-map conn (-> account
                                            (assoc :project project :dashboard dashboard)
                                            (update :user assoc :user/currency [:currency/code "USD"])
                                            (assoc :stripe stripe-user)))]
    (debug "New user created with email:" email)
    ret))

(defn add-transactions
  ([conn project-uuid] (add-transactions conn project-uuid (transactions)))
  ([conn project-uuid transactions]
   (->> transactions
        (map #(assoc % :transaction/uuid (d/squuid)))
        (map #(assoc % :transaction/project {:project/uuid project-uuid}))
        (map #(format/transaction %))
        (transact/transact conn))))

(defn add-currencies [conn]
  (transact/transact conn (f/currencies {:currencies currencies})))

(defn add-conversion-rates [conn]
  (transact/transact conn
                     (f/currency-rates {:date  "2015-10-10"
                                        :rates {:THB 36
                                                :SEK 8.4
                                                :USD 1
                                                :JPY 110.61
                                                :MYR 3.91
                                                :VND 22295.00
                                                :EUR 0.89
                                                :ALL 121.35
                                                :HRK 6.64
                                                :KRW 1189.80
                                                :CNY 6.67}})))

(defn add-data-to-connection
  ([conn] (add-data-to-connection conn (transactions)))
  ([conn transactions & [schema]]
   (let [schemas (or schema (read-schema-files))
         email test-user-email
         project-uuid (d/squuid)]
     (transact/transact-schemas conn schemas)
     (debug "Schema added.")
     (add-currencies conn)
     (debug "Currencies added.")

     (add-verified-user-account conn email project-uuid)
     (debug "New user created and verified.")
     (add-transactions conn project-uuid transactions)
     (debug "User transactions added.")
     (add-conversion-rates conn)
     (debug "Conversion rates added"))))

(defonce connection (atom nil))

(defn transaction-data [& [n]]
  (let [txs (ods/import-parsed (ods/parsed-transactions))]
    (->> (cycle txs) (take (or n (count txs))))))

(defn create-connection []
  (let [uri (env :db-url)]
    (try
      (if (contains? #{nil "" "test"} uri)
        (let [mem-conn (create-new-inmemory-db)]
          (info "Setting up inmemory db because uri is set to:" uri)
          (add-data-to-connection mem-conn (transaction-data))
          (debug "Successfully set up inmemory db!")
          mem-conn)
        (do
          (info "Setting up remote db.")
          (d/connect uri)))
      (catch Exception e
        (error "Exception:" e " when trying to connect to datomic=" uri)
        (throw e)))))

(defn connect!
  "Returns a connection. Caches the connection when it has successfully connected."
  []
  (debug "Will try to set the database connection...")
  ;; Just set the connection once. Using an atom that's only defined once because,
  ;; there's a ring middleware which (seems to) redefine all vars, unless using defonce.
  (if-let [c @connection]
    (do (debug "Already had a connection. Returning the old one: " c)
        c)
    (reset! connection (create-connection))))
