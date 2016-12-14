(ns eponai.client.parser.mutate
  (:require
    [eponai.common.parser :as parser :refer [client-mutate]]
    [eponai.client.auth :as auth]
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug warn]]))

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

(defn logged-in-update-cart [state item]
  (let [cart (db/one-with (db/db state) {:where '[[?e :cart/items]]})]
    (db/transact-one state [:db/add cart :cart/items (:db/id item)])))

(defn anon-update-cart [item]
  #?(:cljs
     (if-let [stored-cart (.getItem js/localStorage "cart")]
       (let [cart (cljs.reader/read-string stored-cart)
             new-cart (update cart :cart/items conj (:db/id item))]
         (.setItem js/localStorage "cart" new-cart))
       (.setItem js/localStorage "cart" {:cart/items [(:db/id item)]}))))

(defmethod client-mutate 'shopping-bag/add-item
  [{:keys [target state]} _ {:keys [item]}]
  (if target
    {:remote/user (auth/is-logged-in?)}
    {:action (fn []
               (if (auth/is-logged-in?)
                 (logged-in-update-cart state item)
                 (anon-update-cart item)))}))

(defmethod client-mutate 'search/search
  [{:keys [target]} _ {:keys [search-string]}]
  (if target
    {:remote true}
    {:action (fn []
               (debug "Mutate search string: " search-string))}))