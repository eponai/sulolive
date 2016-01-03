(ns eponai.client.verify
  (:require [om.next :as om :refer-macros [defui]]
            [cemerick.url :as url]
            [clojure.walk :refer [keywordize-keys]]
            [sablono.core :refer-macros [html]]
            [datascript.core :as d]
            [goog.dom :as gdom]
    ;; To initialize ReactDOM:
            [cljsjs.react.dom]
            [eponai.client.backend :as backend]
            [eponai.client.parser :as parser]))

(enable-console-print!)

;(def state (atom {:verified}))

(defmethod parser/read :query/verification
  [{:keys [state query]} _ _]
  {:value (parser/pull-all state query '[[?e :verification/uuid ?uuid]])
   :remote true})

(defmethod parser/mutate 'email/verify
  [_ _ params]
  (println "Verification verify: " params)
  {:remote true})

(defui Verify
  static om/IQueryParams
  (params [_]
    (let [url (url/url (-> js/window .-location .-href))
          query (:query url)]
      (keywordize-keys query)))                                               ; uuid GET parameter in URL here
  static om/IQuery
  (query [_]
    '[:datascript/schema
      {(:query/verification {:uuid ?uuid})
       [*
        {:verification/status [:db/ident]}
        {:verification/entity [*]}
        {:verification/attribute [:db/ident]}]}])
  Object
  (render [this]
    (let [{[verification] :query/verification} (om/props this)
          {:keys [::did-try-verify]} (om/get-state this)]

      (println "Did try verify: " did-try-verify)
      (cond (not did-try-verify)
            (do
              (om/transact! this `[(email/verify ~(om/get-params this))
                                   :query/verifications])
              (om/update-state! this assoc ::did-try-verify true)
              (html [:div
                     "Verifying email..."]))
            did-try-verify
            (if verification
              (html [:div
                     "Verification successful"])
              (html [:div
                     "Verification failed"]))))))

(defonce conn-atom (atom nil))

(defn init-conn
  "Sets up the datascript state. Caches the state so we can keep our app state between
  figwheel reloads."
  []
  (if @conn-atom
    @conn-atom
    (let [ui-schema {:ui/singleton {:db/unique :db.unique/identity}}
          ui-state [{:ui/singleton :budget/header}
                    {:ui/singleton :ui.singleton/app :app/inited? false}]
          conn (d/create-conn ui-schema)]
      (d/transact! conn ui-state)
      (reset! conn-atom conn))))

(defn run []
  (let [conn (init-conn)
        parser (om/parser {:read   parser/read
                           :mutate parser/mutate})
        reconciler (om/reconciler {:state conn
                                   :parser  parser
                                   :remotes [:remote]
                                   :send    (backend/send! "/verify")
                                   :merge   (backend/merge! conn)})]

    (om/add-root! reconciler Verify (gdom/getElement "my-verify"))))