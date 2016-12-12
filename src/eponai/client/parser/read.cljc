(ns eponai.client.parser.read
  (:require
    [eponai.common.parser :as parser :refer [client-read]]
    [eponai.common.parser.read :as common.read]
    [eponai.common.database :as db]
    [eponai.common :as c]
    #?(:cljs
       [cljs.reader])
    [taoensso.timbre :refer [debug]]
    [eponai.client.auth :as auth]))

;; ################ Local reads  ####################
;; Generic, client only local reads goes here.

;; ################ Remote reads ####################
;; Remote reads goes here. We share these reads
;; with all client platforms (web, ios, android).
;; Local reads should be defined in:
;;     eponai.<platform>.parser.read

;; ----------

(defmethod client-read :query/store
  [{:keys [db query target]} _ {:keys [store-id]}]
  (let [store (db/pull-one-with db query {:where '[[?e]]
                                          :symbols {'?e (c/parse-long store-id)}})]
    (if target
      {:remote true}
      {:value (common.read/multiply-store-items store)})))

(defmethod client-read :query/cart
  [{:keys [db query target]} _ _]
  (debug "REad wuery/cart: " (auth/logged-in-user))
  (if target
    {:remote/user (auth/is-logged-in?)}
    {:value (let [cart (if (auth/is-logged-in?)
                         (db/pull-one-with db query {:where '[[?e :cart/items]]})
                         (db/pull-one-with db query {:where '[[?e :ui.component.cart/items]]}))]
              (debug "Got cart: " cart " active user: " (auth/logged-in-user))
              (common.read/compute-cart-price cart))}))

(defmethod client-read :query/all-items
  [{:keys [db query target]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :item/id]]})}))

(defmethod client-read :query/auth
  [{:keys [target]} _ _]
  (debug "Read query/auth: ")
  (if target
    {:remote true}
    {:value #?(:cljs (.getItem js/localStorage "idToken")
               :clj nil)}))