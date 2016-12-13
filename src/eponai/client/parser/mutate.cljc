(ns eponai.client.parser.mutate
  (:require
    [eponai.common.parser :as parser :refer [client-mutate]]
    [eponai.client.auth :as auth]
    [eponai.common.database :as db]))

;; ################ Remote mutations ####################
;; Remote mutations goes here. We share these mutations
;; with all client platforms (web, ios, android).
;; Local mutations should be defined in:
;;     eponai.<platform>.parser.mutate

(defmethod client-mutate 'session/signout
  [_ _ _]
  {:action (fn []
             #?(:cljs
                (.removeItem js/localStorage "idToken")))})

(defmethod client-mutate 'shopping-bag/add-item
  [{:keys [target state]} _ {:keys [item]}]
  (if target
    {:remote/user (auth/is-logged-in?)}
    {:action (fn []
               (if (auth/is-logged-in?)
                 (let [cart (db/one-with (db/db state) {:where '[[?e :cart/items]]})]
                   (db/transact-one state [:db/add cart :cart/items (:db/id item)]))
                 (db/transact-one state [:db/add [:ui/component :ui.component/cart] :ui.component.cart/items (:db/id item)])))}))