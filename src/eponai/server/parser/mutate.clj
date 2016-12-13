(ns eponai.server.parser.mutate
  (:require
    [eponai.common.database :as db]
    [eponai.common.parser :as parser :refer [server-mutate server-message]]
    [taoensso.timbre :refer [debug info]]))

(defmacro defmutation
  "Creates a message and mutate defmethod at the same time.
  The body takes two maps. The first body is the message and the
  other is the mutate.
  The :return and :exception key in env is only available in the
  message body."
  [sym args message-body mutate-body]
  `(do
     (defmethod server-message (quote ~sym) ~args ~message-body)
     (defmethod server-mutate (quote ~sym) ~args ~mutate-body)))

;; TODO: Make this easier to use. Maybe return {::parser/force-read [:key1 :key2]} in the
;;       return value of the mutate?
(defn force-read-keys! [{:keys [::parser/force-read-without-history] :as env} k & ks]
  (apply swap! force-read-without-history conj k ks)
  nil)


(defmutation shopping-cart/add-item
  [{:keys [state auth]} _ {:keys [item]}]
  {}
  {:action (fn []
             (let [cart (db/one-with (db/db state) {:where '[[?e :cart/items]]})]
               (db/transact-one state [:db/add cart :cart/items (:db/id item)])))})