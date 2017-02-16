(ns eponai.common.stream)

(defn stream-name [store]
  (if-let [id (:db/id store)]
    (str id)
    (throw (ex-info "Store had no :db/id" {:store store}))))

(defn wowza-live-stream-url [subscriber-url stream-name]
  (str subscriber-url "/live/" stream-name "/playlist.m3u8"))