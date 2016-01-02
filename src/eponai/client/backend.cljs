(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [datascript.core :as d]
            [cljs-http.client :as http]
            [eponai.common.datascript :as e.datascript]))

(defn- post [url opts]
  (let [transit-opts {:transit-opts
                      {:decoding-opts
                       {:handlers {"n" cljs.reader/read-string}}}}]
    (http/post url (merge opts transit-opts))))

(defn handle-new-datomic-schema [conn novelty]
  (when-let [datascript-schema (:datascript/schema novelty)]
    (let [current-schema (:schema @conn)
          current-entities (d/q '[:find [(pull ?e [*]) ...] :where [?e]] (d/db conn))
          new-schema (merge-with merge current-schema datascript-schema)
          new-conn (d/create-conn new-schema)]
      ;; Setting the app/inited to true, so we can make desicions about this later. As of writing this,
      ;; it's used to not include the :datascript/schema key for remote reads.
      (d/transact new-conn (concat current-entities [{:ui/singleton :ui.singleton/app :app/inited? true}]))
      (reset! conn @new-conn)))
  (dissoc novelty :datascript/schema))

(defn merge!
  [conn]
  (fn [_ _ novelty]
    (let [novelty (handle-new-datomic-schema conn novelty)
          temp-id-novelty (e.datascript/db-id->temp-id #{} (flatten (vals novelty)))]
      {:keys (keys novelty)
       :next (:db-after @(d/transact conn temp-id-novelty))})))

(defn send!
  []
  (fn [{:keys [remote]} cb]
    (let [remote (->> remote
                      (reduce (fn [query x]
                                (if (vector? x)
                                  (concat query (flatten x))
                                  (conj query x)))
                              []))]
      (prn "send to remote: " remote)
      (go
        (try
          (let [{:keys [body trace-redirects]} (<! (post "/api/user/" {:transit-params remote}))]
            (prn "redirectS: " trace-redirects)
            (cb body))
          (catch :default e
            (prn e)))))))
