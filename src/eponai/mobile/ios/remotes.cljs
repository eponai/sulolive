(ns eponai.mobile.ios.remotes
  (:require [datascript.core :as d]
            [eponai.client.backend :as backend]
            [eponai.common.parser :as parser]
            [om.next :as om]
            [taoensso.timbre :refer-macros [debug]]
            [eponai.client.remotes :as remotes]))

;; TODO: Remove this because we're using eponai.client.remotes instead.

(defn http-call-remote
  "Remote which executes a http call.
  We can use this remote to call api's which are not om.next specific."
  [reconciler-atom]
  (fn [query]
    ;; Do something here?`
    (let [ast (om/query->ast query)
          [[method params]] query]
      (assert (:mutation params) (str "No :mutation in params of query: " query
                                      ". We need a :mutation to be able to "
                                      " decide what to do in parser.merge with"
                                      " the response."))
      (assert (:endpoint params) (str "No :endpoint in params of query: " query))
      (debug "http remote call. ast: " ast " query: " query "method: " method)
      {:method        (condp = method
                        'http/get :get
                        'http/post :post)
       :url           (:endpoint params)
       :opts          (dissoc params :endpoint :mutation)
       :response-fn   (fn [{:keys [status]}]
                        {:status 200 :body {:result {:routing/app-root {}
                                                     method            {:mutation (:mutation params)
                                                                        :status   status}}}})
       :post-merge-fn (fn []
                        (let [reconciler @reconciler-atom]
                          (binding [parser/*parser-allow-local-read* false]
                            (om/transact! reconciler (om/get-query (om/app-root reconciler))))))})))

(defn switching-remote
  "Remote that sends requests to different endpoints.
  Sends to the user-api when the user is logged in, public api otherwise."
  [conn]
  (fn [query]
    (let [auth (d/entity (d/db conn) [:ui/singleton :ui.singleton/auth])
          current-user (:ui.singleton.auth/user auth)
          conf (d/entity (d/db conn) [:ui/singleton :ui.singleton/configuration])
          _ (debug "Remote for current-user status: " (:user/status current-user))
          remote-fn (remotes/post-to-url (if (= (:user/status current-user) :user.status/active)
                                           (:ui.singleton.configuration.endpoints/user-api conf)
                                           (:ui.singleton.configuration.endpoints/api conf)))]
      (remote-fn query))))
