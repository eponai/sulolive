(ns eponai.common.database.pull
  (:require
    [taoensso.timbre :refer [error debug trace info warn]]
    [eponai.common.database :as db]
    [datascript.db]
    [eponai.common.parser.util :as parser]))


(defn project []
  {:where '[[?e :project/uuid]]})

(defn project-with-uuid [project-uuid]
  ;; Harder to check if it's actually a uuid.
  {:pre [(not (string? project-uuid))]}
  {:where   '[[?e :project/uuid ?project-uuid]]
   :symbols {'?project-uuid project-uuid}})

(defn with-auth [query user-uuid]
  (db/merge-query query
                  {:where   '[[?u :user/uuid ?user-uuid]]
                   :symbols {'?user-uuid user-uuid}}))

(defn project-with-auth [user-uuid]
  (with-auth {:where '[[?e :project/users ?u]]} user-uuid))


(defn verifications
  [db user-db-id status]
  {:pre [(db/db-instance? db)
         (number? user-db-id)
         (keyword? status)]}
  (db/q '[:find [?v ...]
       :in $ ?u ?s
       :where
       [?v :verification/entity ?u]
       [?u :user/email ?e]
       [?v :verification/value ?e]
       [?v :verification/status ?s]]
     db
     user-db-id
     status))

;; ############################# Transactions #############################

(defn transaction-entity-query [{:keys [project-eid user-uuid]}]
  (assert (some? user-uuid) "User UUID must be supplied to read transactions.")
  (assert (number? project-eid) (str "Project eid must be a number to read transactions. Was: " project-eid))
  {:where   '[[?u :user/uuid ?user-uuid]
              [?p :project/users ?u]
              [?e :transaction/project ?p]]
   :symbols {'?user-uuid user-uuid
             '?p         project-eid}})

;; TODO: This is probably slow. We want to do a separate
;;       query for transaction conversions and user
;;       conversions.
(defn find-conversions [db tx-ids user-uuid]
  (->> (db/all-with db {:find '[?t ?e ?e2]
                     :symbols      {'[?t ...] tx-ids
                                    '?uuid    user-uuid}
                     :where        '[[?t :transaction/date ?d]
                                     [?e :conversion/date ?d]
                                     [?t :transaction/currency ?cur]
                                     [?e :conversion/currency ?cur]
                                     [?u :user/uuid ?uuid]
                                     [?u :user/currency ?u-cur]
                                     [?e2 :conversion/currency ?u-cur]
                                     [?e2 :conversion/date ?d]]})
       (sequence (comp (mapcat (juxt second last))
                       (distinct)))))

(def conversion-query (parser/put-db-id-in-query
                        [{:conversion/date [:date/ymd]}
                         :conversion/rate
                         {:conversion/currency [:currency/code]}]))

(defn conversions [db tx-ids user-uuid]
  (db/pull-many db conversion-query (find-conversions db tx-ids user-uuid)))

(defn transaction-with-conversion-fn [db transaction-convs user-convs]
  {:pre [(delay? transaction-convs) (delay? user-convs)]}
  (let [approx-tx-conv (memoize
                         (fn [curr]
                           ;; (debug "Approximating: " curr)
                           (or (some->> curr (get @transaction-convs) (first) (val))
                               (when curr
                                 (db/one-with db {:where [['?e :conversion/currency curr]]})))))
        approx-user-curr (memoize
                           (fn []
                             (some-> @user-convs (first) (val))))]
    (fn [transaction]
      ;;#?(:cljs (debug "executing transaction-with-conversion on tx: " (:db/id transaction)))
      (let [tx-date (get-in transaction [:transaction/date :db/id])
            curr->conv (fn [curr]
                         (or (get-in @transaction-convs [curr tx-date])
                             (approx-tx-conv curr)))
            tx-curr (get-in transaction [:transaction/currency :db/id])
            tx-conv (curr->conv tx-curr)
            user-conv (get @user-convs tx-date)
            user-conv (or user-conv (approx-user-curr))]
        (if (and (some? tx-conv) (some? user-conv))
          (let [;; All rates are relative USD so we need to pull what rates the user currency has,
                ;; so we can convert the rate appropriately for the user's selected currency
                conv->rate (fn [conv]
                             (let [transaction-conversion (db/entity* db conv)
                                   user-currency-conversion (db/entity* db user-conv)
                                   #?@(:cljs [rate (/ (:conversion/rate transaction-conversion)
                                                      (:conversion/rate user-currency-conversion))]
                                       :clj  [rate (with-precision 10 (bigdec (/ (:conversion/rate transaction-conversion)
                                                                                 (:conversion/rate user-currency-conversion))))])]
                               rate))
                curr->conversion-map (fn [curr]
                                       (when curr
                                         (let [conv (curr->conv curr)
                                               rate (conv->rate conv)]
                                           {:conversion/currency {:db/id curr}
                                            :conversion/rate     rate
                                            :conversion/date     (:conversion/date (db/entity* db conv))})))]
            ;; Convert the rate from USD to whatever currency the user has set
            ;; (e.g. user is using SEK, so the new rate will be
            ;; conversion-of-transaction-currency / conversion-of-user-currency
            [(:db/id transaction)
             (merge (curr->conversion-map tx-curr)
                    {:user-conversion-id             user-conv
                     :transaction-conversion-id      tx-conv
                     ::currency-to-conversion-map-fn curr->conversion-map})])
          (debug "No tx-conv or user-conv. Tx-conv: " tx-conv
                 " user-conv: " user-conv
                 " for transaction: " (into {} transaction)))))))

(defn absolute-fee? [fee]
  #(= :transaction.fee.type/absolute (:transaction.fee/type fee)))

(defn same-conversions? [db user-curr]
  (let [user-conv->user-curr (memoize
                               (fn [user-conv]
                                 (get-in (db/entity* db user-conv) [:conversion/currency :db/id])))]
    (fn [tx]
      (letfn [(same-conversion? [currency conversion]
                (and (= user-curr
                        (user-conv->user-curr (:user-conversion-id conversion)))
                     (= (get-in currency [:db/id])
                        (get-in conversion [:conversion/currency :db/id]))))]
        (every? (fn [[curr conv]] (same-conversion? curr conv))
                (cons [(:transaction/currency tx)
                       (:transaction/conversion tx)]
                      (->> (:transaction/fees tx)
                           (filter absolute-fee?)
                           (map (juxt :transaction.fee/currency :transaction.fee/conversion)))))))))

(defn transaction-conversions [db user-uuid transaction-entities]
  ;; user currency is always the same
  ;; transaction/dates and currencies are shared across multiple transactions.
  ;; Must be possible to pre-compute some table between date<->conversion<->currency.
  (let [currency-date-pairs (delay
                              (transduce (comp (mapcat #(map vector
                                                             (cons (get-in % [:transaction/currency :db/id])
                                                                   (->> (:transaction/fees %)
                                                                        (map (fn [fee]
                                                                               (get-in fee [:transaction.fee/currency :db/id])))
                                                                        (filter some?)))
                                                             (repeat (get-in % [:transaction/date :db/id]))))
                                               (distinct))
                                         (completing (fn [m [k v]]
                                                       {:pre [(number? k) (number? v)]}
                                                       (assoc m k (conj (get m k []) v))))
                                         {}
                                         transaction-entities))
        transaction-convs (delay
                            (->> (db/find-with db {:find '[?currency ?date ?conv]
                                                :symbols      {'[[?currency [?date ...]] ...] @currency-date-pairs}
                                                :where        '[[?conv :conversion/currency ?currency]
                                                                [?conv :conversion/date ?date]]})
                                 (reduce (fn [m [curr date conv]]
                                           (assoc-in m [curr date] conv))
                                         {})))
        user-curr (db/one-with db {:where   '[[?u :user/uuid ?user-uuid]
                                           [?u :user/currency ?e]]
                                :symbols {'?user-uuid user-uuid}})
        user-convs (delay
                     (when user-curr
                       (let [convs (into {}
                                         (db/find-with db {:find '[?date ?conv]
                                                        :symbols      {'[?date ...] (reduce into #{} (vals @currency-date-pairs))
                                                                       '?user-curr  user-curr}
                                                        :where        '[[?conv :conversion/currency ?user-curr]
                                                                        [?conv :conversion/date ?date]]}))]
                         (if (seq convs)
                           convs
                           ;; When there are no conversions for any of the transaction's dates, just pick any one.
                           (let [one-conv (db/entity* db (db/one-with db {:where   '[[?e :conversion/currency ?user-curr]]
                                                                    :symbols {'?user-curr user-curr}}))]
                             {(get-in one-conv [:conversion/date :db/id]) (:db/id one-conv)})))))]
    (into {}
          ;; Skip transactions that has a conversion with the same currency.
          (comp (remove (same-conversions? db user-curr))
                (map (transaction-with-conversion-fn db transaction-convs user-convs))
                (filter some?))
          transaction-entities)))

(defn assoc-conversion-xf [conversions]
  (map (fn [tx]
         {:pre [(:db/id tx)]}
         (let [tx-conversion (get conversions (:db/id tx))
               fee-with-conv (fn [fee]
                               (if-not (absolute-fee? fee)
                                 fee
                                 (let [curr->conv (::currency-to-conversion-map-fn tx-conversion)
                                       _ (assert (fn? curr->conv))
                                       fee-conv (curr->conv (get-in fee [:transaction.fee/currency :db/id]))]
                                   (cond-> fee (some? fee-conv) (assoc :transaction.fee/conversion fee-conv)))))]
           (cond-> tx
                   (some? tx-conversion)
                   (-> (assoc :transaction/conversion (dissoc tx-conversion ::currency-to-conversion-map-fn))
                       (update :transaction/fees #(into #{} (map fee-with-conv) %))))))))

(defn transactions-with-conversions [db user-uuid tx-entities]
  (let [conversions (transaction-conversions db user-uuid tx-entities)]
    (into [] (assoc-conversion-xf conversions) tx-entities)))

;; ############################# Widgets #############################


(defn transaction-query []
  (parser/put-db-id-in-query
    '[:transaction/uuid
      :transaction/amount
      :transaction/conversion
      {:transaction/type [:db/ident]}
      {:transaction/currency [:currency/code]}
      {:transaction/tags [:tag/name]}
      {:transaction/date [*]}]))
