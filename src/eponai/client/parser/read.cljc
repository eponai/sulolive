(ns eponai.client.parser.read
  (:require
    [eponai.common.parser :as parser :refer [client-read]]
    [eponai.common.parser.read :as common.read]
    [eponai.common.database :as db]
    [eponai.common :as c]
    #?(:cljs
       [cljs.reader])))

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
  (let [cart (db/pull-one-with db query {:where '[[?e :cart/items]]})]
    (if target
      {:remote true}
      {:value (common.read/compute-cart-price cart)})))

(defmethod client-read :query/all-items
  [{:keys [db query target]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :item/id]]})}))