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
    [eponai.client.reconciler :as client.reconciler]
    [eponai.client.utils :as client.utils]
    [eponai.common.parser :as parser]
    [eponai.common.routes :as routes]
    [eponai.server.auth :as auth]
    [eponai.server.ui.root :as root]
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
        parser (parser/client-parser (parser/client-parser-state {:query-params (:query-params request-env)}))
        send-fn (server-send request-env reconciler-atom )
        reconciler (client.reconciler/create {:conn         (datascript/conn-from-db (:empty-datascript-db request-env))
                                              :parser       parser
                                              :send-fn      send-fn
                                              :history      2
                                              :route        (:route request-env)
                                              :route-params (:route-params request-env)})]
    (reset! reconciler-atom reconciler)
    (client.utils/init-state! reconciler send-fn component)
    reconciler))

(defn render-page [env]
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
    (fn [props]
      (debug "COMPONENT: " (pr-str component))
      (with-doctype
        (html/render-html-without-reactid-tags
          (->component (assoc props ::root/app-html (render-page props))))))))

(def render-site (makesite root/Root))
