(ns eponai.common.stream)

(defn stream-id [store]
  (if-let [id (:db/id store)]
    (str id)
    (throw (ex-info "Store had no :db/id" {:store store}))))

(defn wowza-live-stream-url [subscriber-url stream-id]
  (str subscriber-url "/live/" stream-id "/playlist.m3u8"))

(defn wowza-live-thumbnail-small [subscriber-url stream-id]
  (str subscriber-url "/thumbnail?application=live&format=jpg&streamname=" stream-id "&size=" "640x360"))

(defn wowza-live-thumbnail-large [subscriber-url stream-id]
  (str subscriber-url "/thumbnail?application=live&format=jpg&streamname=" stream-id "&size=" "1280x720"))