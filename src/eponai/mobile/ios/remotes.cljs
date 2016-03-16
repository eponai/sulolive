(ns eponai.mobile.ios.remotes
  (:require [datascript.core :as d]
            [eponai.client.backend :as backend]
            [om.next :as om]
            [taoensso.timbre :refer-macros [debug]]))


(defn http-call-remote
  "Remote which executes a http call.
  We can use this remote to call api's which are not om.next specific."
  []
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
      {:method (condp = method
                 'http/get :get
                 'http/post :post)
       :url    (:endpoint params)
       :opts   (dissoc params :endpoint :mutation)
       :response-fn (fn [{:keys [status]}]
                      {:status 200 :body {:proxy/route-data {}
                                          method {:mutation (:mutation params)
                                                  :status status}}})})))

(defn switching-remote
  "Remote that sends requests to different endpoints.
  Sends to the user-api when the user is logged in, public api otherwise."
  [conn]
  (fn [query]
    (let [conf (d/entity (d/db conn) [:ui/singleton :ui.singleton/configuration])
          remote-fn (backend/post-to-url (if (:ui.singleton.configuration/logged-in conf)
                                           (:ui.singleton.configuration.endpoints/user-api conf)
                                           (:ui.singleton.configuration.endpoints/api conf)))]
      (remote-fn query))))
