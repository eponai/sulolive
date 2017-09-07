(ns eponai.server.external.vods
  (:require
    [eponai.server.external.aws-s3 :as s3]
    [clojure.string :as str]
    [eponai.common]
    [com.stuartsierra.component :as component]
    [suspendable.core :as suspendable]
    [eponai.common.database :as db]))

(defn -list-vods
  ([s3] (-list-vods s3 nil))
  ([s3 store-id]
    ;; TODO: List objects such that we don't have to realize the entire s3 bucket.
   (->> (s3/list-objects s3 {:bucket-name "sulo-live-vods"
                             :prefix      (str "wowza/" store-id)
                             :delimiter   "sulo_vod/segments"})
        (sequence
          (comp (map :key)
                (filter #(.endsWith ^String % ".m3u8"))
                (map (fn [path]
                       (let [[_ store-id timestamp] (str/split path #"/")]
                         {:vod/store     (eponai.common/parse-long-safe store-id)
                          :vod/timestamp (eponai.common/parse-long-safe timestamp)
                          :vod/playlist  path})))))
        (sort-by :vod/timestamp #(compare %2 %1)))))

(defprotocol IVodStorage
  (all-vods [this] "Returns vods sorted by time cached.")
  (vods-by-store [this store-id] "Returns vods by store cached.")
  (update-store-vods! [this store-id] "Call when a store might have gotten a vod "))

(defrecord VodStorage [aws-s3]
  component/Lifecycle
  (start [this]
    (if (:started? this)
      this
      (assoc this :started? true
                  :state (atom (group-by :vod/store (-list-vods aws-s3))))))
  (stop [this]
    (dissoc this :started? :state))

  suspendable/Suspendable
  (suspend [this] (assoc this :aws-s3 aws-s3))
  (resume [this old-this]
    (reduce-kv assoc
               (component/start this)
               (select-keys old-this [:state :aws-s3])))

  IVodStorage
  (all-vods [this]
    (sort-by :vod/timestamp (mapcat val @(:state this))))
  (vods-by-store [this store-id]
    (get @(:state this) store-id))
  (update-store-vods! [this store-id]
    (future
      #(swap! (:state this) assoc store-id (-list-vods aws-s3 store-id)))))

(defrecord FakeVodStorage [datomic]
  component/Lifecycle
  (start [this]
    (if (:started? this)
      this
      (assoc this
        :started? true
        :vods (->> (db/all-with (db/db datomic) {:where '[[?e :store/items]]})
                   (map (fn [store-id]
                          {:vod/store     store-id
                           :vod/timestamp (- (System/currentTimeMillis) (rand-int (* 3600 24 60)))}))
                   (group-by :vod/store)))))
  (stop [this]
    (dissoc this :started? :vods))

  IVodStorage
  (all-vods [this]
    (sort-by :vod/timestamp (mapcat val (:vods this))))
  (vods-by-store [this store-id]
    (get (:vods this) store-id))
  (update-store-vods! [this store-id]))
