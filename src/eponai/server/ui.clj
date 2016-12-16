(ns eponai.server.ui
  (:require
    [om.next :as om]
    [om.dom :as dom]
    [om.next.cache :as om.cache]
    [datascript.core :as datascript]
    [eponai.server.ui.html :as html]
    [eponai.common.database :as db]
    [eponai.client.parser.merge :as merge]
    [eponai.client.parser.mutate]
    [eponai.client.parser.read]
    [eponai.client.utils :as client.utils]
    [eponai.common.parser :as parser]
    [eponai.common.routes :as routes]
    [eponai.common.parser.util :as parser.util]
    [eponai.common.ui.store :as common.store]
    [eponai.server.auth :as auth]
    [eponai.server.ui.goods :as goods]
    [eponai.server.ui.product :as product]
    [eponai.server.ui.index :as index]
    [eponai.server.ui.store :as store]
    [eponai.server.ui.checkout :as checkout]
    [eponai.server.ui.streams :as streams]
    [eponai.server.ui.root :as root]
    [eponai.server.ui.common :as common]
    [taoensso.timbre :as timbre :refer [debug]]
    [eponai.common.ui.router :as router]))

(defn server-send [server-env reconciler-atom]
  (fn [queries cb]
    (run! (fn [[remote-key query]]
            ;; We don't need to query for datascript/schema since
            ;; we've already set up the datascript instance.
            (let [query (remove #(= % :datascript/schema) query)
                  res ((parser/server-parser) server-env query)]
              (cb {:db     (db/db (om/app-state @reconciler-atom))
                   :result res})))
          queries)))

(defn make-reconciler [request-env component]
  (let [reconciler-atom (atom nil)
        parser (parser/client-parser (parser/client-parser-state {::parser/get-route-params #(:route-params request-env)}))
        ;; TODO: Is this parser wrapper needed?
        parser (fn [env query & [target]]
                 (parser (merge env (dissoc request-env :state :params)) query target))
        remotes [:remote :remote/user]
        send-fn (server-send request-env reconciler-atom )
        reconciler (om/reconciler {:state   (datascript/conn-from-db (:empty-datascript-db request-env))
                                   :parser  parser
                                   :remotes remotes
                                   :send    send-fn
                                   :merge   (merge/merge!)
                                   :history 2
                                   :migrate nil})]
    (reset! reconciler-atom reconciler)
    (binding [parser/*parser-allow-remote* false]
      (om/transact! reconciler [(list 'routes/set-route! (select-keys request-env [:route :route-params]))]))
    (client.utils/init-state! reconciler remotes send-fn parser component)
    reconciler))

(defn component-to-html-str-fn [env]
  (fn [component]
    (let [reconciler (make-reconciler env component)
          ui-root (om/add-root! reconciler component nil)
          html-string (dom/render-to-str ui-root)]
      (html/raw-string html-string))))


(defn render-env [env]
  (let [component router/Router
        reconciler (make-reconciler env component)
        ui-root (om/add-root! reconciler component nil)
        html-string (dom/render-to-str ui-root)]
    (html/raw-string html-string)))


(defn with-doctype [html-str]
  (str "<!DOCTYPE html>" html-str))

(defn render-to-str [component props]
  {:pre [(some? (:release? props))]}
  (with-doctype (dom/render-to-str ((om/factory component) props))))

(defn makesite [component]
  (let [->component (om/factory component)]
    (fn [env]
      (debug "COMPONENT: " (pr-str component))
      (with-doctype
        (html/render-html-without-reactid-tags
          (->component (assoc env ::render-component-as-html (component-to-html-str-fn env)
                                  ::root/app-html (render-env env))))))))

(def auth-html (makesite auth/Auth))
(def goods-html (makesite root/Root))
(def product-html (makesite root/Root))
(def index-html (makesite root/Root))
(def store-html (makesite root/Root))
(def checkout-html (makesite root/Root))
(def streams-html (makesite root/Root))