(ns eponai.client.remotes
  (:require [datascript.core :as d]
            [eponai.common.parser :as parser]
            [eponai.client.auth :as auth]
            [om.next :as om]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [info debug]]))


#?(:cljs
   (defn load-url-on-redirect [remote]
     (letfn [(redirect [{:keys [status headers] :as response}]
               (assert (and (number? status) (<= 300 status 399))
                       (str "Status was a number in the 300 to 399 range. Was: " status))
               (assert (map? headers) (str "headers was not a map in response: " response))
               (let [location (some #(get headers %) ["location" "Location"])]
                 (assert (string? location) (str "Value for location was not a string, was: " location))
                 ;;TODO: This doesn't work for mobile? react-native?
                 (set! js/window.location location)))]
       (assoc remote :redirect-fn redirect))))

;; Basic remote function

(defn post-to-url
  "Takes a url and returns a remote which can be passed to eponai.client.backend/send! {:remote <here>}.
  This one will post to the url with transit-params."
  [url]
  (fn [query]
    (-> {:method      :post
         :url         url
         :opts        {:transit-params {:query query}}
         :response-fn identity}
        #?(:cljs load-url-on-redirect))))

(defn wrap-update [remote-fn k f & args]
  (fn [query]
    (apply update (remote-fn query) k f args)))

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
      (assoc-in ret [:opts :transit-params ::parser/read-basis-t]
                (:eponai.common.parser.read-basis-t/map
                  (d/entity db [:db/ident ::parser/read-basis-t]))))))

(defn- force-remote-read! [reconciler]
  (binding [parser/*parser-allow-local-read* false]
    (om/transact! reconciler (om/get-query (om/app-root reconciler)))))

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
       :post-merge-fn #(force-remote-read! @reconciler-atom)})))

(defn switching-remote
  "Remote that sends requests to different endpoints.
  Sends to the user-api when the user is logged in, public api otherwise."
  [conn]
  (fn [query]
    (comment
      "This is with old jourmoney configuration details.
      We may want to do this an entire different way."
      (let [db (d/db conn)
           conf (d/entity db [:ui/singleton :ui.singleton/configuration])
           remote-fn (post-to-url (if (auth/has-active-user? db)
                                    (:ui.singleton.configuration.endpoints/user-api conf)
                                    (:ui.singleton.configuration.endpoints/api conf)))]
       (remote-fn query)))
    (throw (ex-info "TODO" {:todo "implement function"
                            :details "see comments in this function"}))))

(defn read-remote-on-auth-change [remote-fn reconciler-atom]
  (fn [query]
    (let [logged-in? (auth/has-active-user? (d/db (om/app-state @reconciler-atom)))
          remote (remote-fn query)]
      (update remote :post-merge-fn
              (fn [post-merge-fn]
                (fn []
                  (when post-merge-fn
                    (post-merge-fn))
                  (let [reconciler @reconciler-atom
                        logged-in-after-merge? (auth/has-active-user? (d/db (om/app-state reconciler)))]
                    (if (not= logged-in? logged-in-after-merge?)
                      (do (info "LOGGED IN STATUS CHANGED IN POST MERGE: "
                                [:pre logged-in? :post logged-in-after-merge?])
                          (force-remote-read! reconciler))
                      (debug "Logged in status hasn't change in post merge. Is still: " logged-in?)))))))))