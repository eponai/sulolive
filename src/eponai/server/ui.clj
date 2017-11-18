(ns eponai.server.ui
  (:require
    [eponai.common.ui-namespaces]
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
    [eponai.common.ui.router :as router]
    [eponai.server.log :as log]
    [eponai.client.client-env :as client-env]
    [environ.core :as env]))

(defn server-send [server-env reconciler-atom]
  (fn [queries cb]
    (run! (fn [[remote-key query]]
            ;; We don't need to query for datascript/schema since
            ;; we've already set up the datascript instance.
            (let [query (into [] (remove #(= % :datascript/schema)) query)
                  res ((parser/server-parser) server-env query)]
              (cb {:db     (db/db (om/app-state @reconciler-atom))
                   :result res})))
          queries)))

;; The query is static, so we might as well just compute it once.
(def router-query (om/get-query router/Router))
(def ->Router (om/factory router/Router))

(defn make-reconciler [request-env]
  (let [reconciler-atom (atom nil)
        parser (parser/client-parser)
        send-fn (server-send request-env reconciler-atom)
        shared-config (cond
                        (some? (env/env :sulo-demo))
                        {:shared/photos      :env/prod
                         :shared/vods        :env/dev
                         :shared/live-stream :env/dev}
                        (:release? request-env)
                        (constantly :env/prod)
                        :else
                        (constantly :env/dev))
        reconciler (client.reconciler/create {:conn               (datascript/conn-from-db (:empty-datascript-db request-env))
                                              :parser             parser
                                              :send-fn            send-fn
                                              :shared/photos      (shared-config :shared/photos)
                                              :shared/vods        (shared-config :shared/vods)
                                              :shared/live-stream (shared-config :shared/live-stream)
                                              :shared/client-env  (:system/client-env (:system request-env))
                                              :history            2
                                              :route              (:route request-env)
                                              :route-params       (:route-params request-env)
                                              :query-params       (:query-params request-env)})]
    (reset! reconciler-atom reconciler)
    reconciler))

(defn render-root! [reconciler]
  (let [parse (client.utils/parse reconciler router-query nil)]
    (binding [om/*reconciler* reconciler
              om/*shared* (merge (get-in reconciler [:config :shared])
                                 (when-let [shared-fn (get-in reconciler [:config :shared-fn])]
                                   (shared-fn parse)))
              om/*instrument* (get-in reconciler [:config :instrument])]
      (->Router parse))))

(defn render-page [env]
  (let [reconciler (make-reconciler env)
        _ (client.utils/init-state! reconciler (get-in reconciler [:config :send]) router-query)
        ui-root (render-root! reconciler)
        html-string (dom/render-to-str ui-root)]
    {:html (html/raw-string html-string)
     :reconciler reconciler}))

(defn with-doctype [^StringBuilder html-sb]
  (doto html-sb
    (.insert 0 "<!DOCTYPE html>")))

(defn render-to-str [component props]
  {:pre [(some? (:release? props))]}
  (with-doctype (dom/render-to-str ((om/factory component) props))))

(defn render-site [props]
  (let [start (System/currentTimeMillis)
        page (render-page props)
        html (with-doctype
               (html/render-html-without-reactid-tags
                 (root/->Root (assoc props ::root/app-html (:html page)
                                           ::root/reconciler (:reconciler page)))))
        end (System/currentTimeMillis)]
    (log/info! (:logger props) ::render-html {:event-time-ms (- end start)})
    (str html)))

