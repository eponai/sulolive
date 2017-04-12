(ns eponai.common.stream)

(defn stream-id [store]
  (if-let [id (:db/id store)]
    (str id)
    (throw (ex-info "Store had no :db/id" {:store store}))))

(defn wowza-live-stream-url [subscriber-url stream-id]
  (str subscriber-url "/live/" stream-id "/playlist.m3u8"))