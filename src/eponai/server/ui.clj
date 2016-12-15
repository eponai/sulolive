(ns eponai.server.ui
  (:require
    [om.next :as om]
    [om.dom :as dom]
    [datascript.core :as datascript]
    [eponai.server.ui.html :as html]
    [eponai.common.database :as db]
    [eponai.client.parser.merge :as merge]
    [eponai.client.parser.mutate]
    [eponai.client.parser.read]
    [eponai.client.utils :as client.utils]
    [eponai.common.parser :as parser]
    [eponai.common.parser.util :as parser.util]
    [eponai.common.ui.store :as common.store]
    [eponai.server.auth :as auth]
    [eponai.server.ui.goods :as goods]
    [eponai.server.ui.product :as product]
    [eponai.server.ui.index :as index]
    [eponai.server.ui.store :as store]
    [eponai.server.ui.checkout :as checkout]
    [eponai.server.ui.streams :as streams]
    [eponai.server.ui.common :as common]
    [taoensso.timbre :as timbre :refer [debug]]))

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
        parser (parser/client-parser)
        parser (fn [env query & [target]]
                 (parser (merge env (dissoc request-env :state)) query target))
        remotes [:remote :remote/user]
        send-fn (server-send request-env reconciler-atom )
        reconciler (om/reconciler {:state   (datascript/conn-from-db (:empty-datascript-db request-env))
                                   :parser  parser
                                   :remotes remotes
                                   :send    send-fn
                                   :merge   (merge/merge!)
                                   :migrate nil})]
    (reset! reconciler-atom reconciler)
    (client.utils/init-state! reconciler remotes send-fn parser component)
    reconciler))

(defn component-to-html-str-fn [env]
  (fn [component]
    (let [reconciler (make-reconciler env component)
          ui-root (om/add-root! reconciler component nil)
          html-string (dom/render-to-str ui-root)]
      (html/raw-string html-string))))

(defn with-doctype [html-str]
  (str "<!DOCTYPE html>" html-str))

(defn render-to-str [component props]
  {:pre [(some? (:release? props))]}
  (with-doctype (dom/render-to-str ((om/factory component) props))))

(defn makesite [component]
  (let [->component (om/factory component)]
    (fn [env]
      (with-doctype
        (html/render-html-without-reactid-tags
          (->component (assoc env ::render-component-as-html
                                  (component-to-html-str-fn env))))))))

(def auth-html (makesite auth/Auth))
(def goods-html (makesite goods/Goods))
(def product-html (makesite product/Product))
(def index-html (makesite index/Index))
(def store-html (makesite store/Store))
(def checkout-html (makesite checkout/Checkout))
(def streams-html (makesite streams/Streams))