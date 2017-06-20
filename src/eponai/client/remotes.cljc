(ns eponai.client.remotes
  (:require [datascript.core :as d]
            [eponai.common.parser :as parser]
            [eponai.common.database :as db]
            [eponai.client.backend :as backend]
            [eponai.client.auth :as auth]
            [om.next :as om]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [info debug]]
            [eponai.client.chat :as chat]
            [eponai.client.routes :as routes]))


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

(defn update-key
  "Updates the current value of a remote-fn value for key k with function f"
  [remote-fn k f & args]
  (fn [query]
    (apply update (remote-fn query) k f args)))

(defn with-auth [remote-fn conn]
  (fn [query]
    (-> (remote-fn query)
        (assoc-in [:opts :transit-params :auth :email]
                  (auth/authed-email (db/db conn))))))

(defn with-route [remote-fn conn]
  (fn [query]
    (-> (remote-fn query)
        (assoc-in [:opts :transit-params :route-map]
                  (routes/current-route (db/db conn))))))

(defn read-basis-t-remote-middleware
  "Given a remote-fn (that describes what, where and how to send a request to a server),
  add basis-t for each key to the request. basis-t represents at which basis-t we last
  read a key from the remote db."
  [remote-fn conn]
  (fn [query]
    (let [ret (remote-fn query)]
      (assoc-in ret [:opts :transit-params ::parser/read-basis-t]
                (::parser/read-basis-t-graph
                  (d/entity (d/db conn) [:ui/singleton ::parser/read-basis-t]))))))

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

(defn send-with-chat-update-basis-t [remote-fn reconciler-atom]
  (fn [query]
    ;; include chat-update-basis-t for read-only queries.
    (if (some parser/mutation? query)
      (do
        (debug "Found mutation in query: " query " Won't include chat-update-basis-t with it.")
        (remote-fn query))
      (let [db (db/to-db @reconciler-atom)
            store-id (chat/current-store-id db)]

        (if-not (some? store-id)
          (do
            (debug "Could not get a store-id. Avoiding send of query: " query)
            {::backend/skip? true})
          (let [chat-update-basis-t (chat/queued-basis-t db store-id)
                current-basis-t (chat/chat-basis-t db store-id)]
            (if (and (some? current-basis-t) (>= current-basis-t (or chat-update-basis-t 0)))
              (do
                (debug "Current basis-t >= chat-update basis-t: " {:current-basis-t current-basis-t :update-basis-t chat-update-basis-t}
                       " Avoiding send of query: " query)
                {::backend/skip? true})
              (do
                (debug "Will really send the chat read-only request for: " {:store-id            store-id
                                                                            :current-basis-t     current-basis-t
                                                                            :chat-update-basis-t chat-update-basis-t})
                (-> (remote-fn query)
                    (assoc-in [:opts :transit-params ::parser/chat-update-basis-t] chat-update-basis-t))))))))))