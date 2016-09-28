(ns eponai.client.remotes
  (:require [datascript.core :as d]
            [eponai.common.parser :as parser]
            [om.next :as om]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug]]))

;; Basic remote function

(defn post-to-url
  "Takes a url and returns a remote which can be passed to eponai.client.backend/send! {:remote <here>}.
  This one will post to the url with transit-params."
  [url]
  (fn [query]
    {:method      :post
     :url         url
     :opts        {:transit-params {:query query}}
     :response-fn identity}))


(defn read-basis-t-remote-middleware
  "Given a remote-fn (that describes what, where and how to send a request to a server),
  add basis-t for each key to the request. basis-t represents at which basis-t we last
  read a key from the remote db."
  [remote-fn conn]
  (fn [query]
    (let [ret (remote-fn query)
          db (d/db conn)]
      ;; TODO: Create a protocol for the read-basis-t stuff?
      ;;       This would make it easier to understand what's stored in datascript.
      (assoc-in ret [:opts :transit-params :eponai.common.parser/read-basis-t]
                (some->> (d/q '{:find [?e .] :where [[?e :db/ident :eponai.common.parser/read-basis-t]]}
                              db)
                         (d/entity db)
                         (d/touch)
                         (into {}))))))

(defn http-call-remote
  "Remote which executes a http call.
  We can use this remote to call api's which are not om.next specific."
  [reconciler-atom]
  (fn [query]
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
          remote-fn (post-to-url (if (= (:user/status current-user) :user.status/active)
                                     (:ui.singleton.configuration.endpoints/user-api conf)
                                     (:ui.singleton.configuration.endpoints/api conf)))]
      (remote-fn query))))
