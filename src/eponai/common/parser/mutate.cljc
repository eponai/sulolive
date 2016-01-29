(ns eponai.common.parser.mutate
  (:require [clojure.set :refer [rename-keys]]
            [eponai.common.format :as format]
            [eponai.common.validate :as validate]
            [eponai.common.database.transact :as transact]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn]]
    #?(:clj
            [eponai.server.api :as api])

    #?(:clj
            [clojure.core.async :refer [go >! chan]])
    #?(:cljs [datascript.core :as d])
    #?(:cljs [om.next :as om])
            [eponai.common.format :as f]))

(defmulti mutate (fn [_ k _] k))

;; -------- Remote mutations

(defn- transaction-create [{:keys [state db auth]} k {:keys [input-tags] :as params}]
  (fn []
    (when-not (= (frequencies (set input-tags))
                 (frequencies input-tags))
      (throw (ex-info "Illegal argument :input-tags. Each tag must be unique."
                      {:input-tags input-tags
                       :mutate     k
                       :params     params})))
    (let [#?@(:clj [currency-chan (chan 1)])
          db-tx (format/input-transaction->db-entity params)
          _ (validate/valid-user-transaction? db-tx)]
      (transact/transact state [db-tx])
      #?(:clj (go (>! currency-chan (:transaction/date db-tx))))
      #?(:clj {:currency-chan currency-chan}
         :cljs nil))))

(defmethod mutate 'transaction/create
  [env k params]
  {:action (transaction-create env k params)
   #?@(:cljs [:remote true])})

(defmethod mutate 'budget/create
  [{:keys [state auth]} _ {:keys [input-uuid input-budget-name]}]
  (debug "budget/create for uuid:" input-uuid)
  #?(:cljs {:remote true}
     :clj  {:action (fn []
                      (transact/transact state [(f/budget->db-tx [:user/uuid (:username auth)]
                                                                 input-uuid
                                                                 input-budget-name)])
                      true)}))

(defmethod mutate 'signup/email
  [{:keys [state]} _ params]
  (debug "signup/email with params:" params)
  #?(:cljs {:remote true}
     :clj  {:action (fn []
                      {:email-chan (api/signin state (:input-email params))})}))

(defmethod mutate 'stripe/charge
  [{:keys [state]} _ {:keys [token]}]
  #?(:cljs {:remote true}
     :clj  {:action (fn []
                      (api/stripe-charge token))}))
