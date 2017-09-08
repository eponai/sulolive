(ns eponai.client.videos
  (:require
    [eponai.common.shared :as shared]
    [eponai.client.client-env :as client-env]
    [clojure.string :as string]))

(defprotocol IVodProvider
  (vod-url [this store-id timestamp] "Returns a vod url given a store and the start time of the vod.")
  (vod-thumbnail-url [this store-id timestamp] "Returns thumbnail for the vod."))

(defprotocol ILiveStreamProvider
  (live-stream-url [this store-id] "Returns a url to the live stream of a store")
  (live-stream-thumbnail-url [this store-id size] "Returns a thumbnail for the current live stream."))

(defmethod shared/shared-component [:shared/vods :env/prod]
  [_ _ _]
  (reify IVodProvider
    (vod-url [this store-id timestamp]
      (str "https://vods.sulo.live/wowza/" store-id "/" timestamp "/sulo_vod/playlist.m3u8"))
    (vod-thumbnail-url [this store-id timestamp]
      (-> (vod-url this store-id timestamp)
          (string/replace "playlist.m3u8" "playlist.jpg")))))

(defmethod shared/shared-component [:shared/vods :env/dev]
  [_ _ _]
  ;; For every store and timestamp, just return the same url to fake it.
  (reify IVodProvider
    (vod-url [_ _ _]
      "https://vods.sulo.live/wowza/17592186045420/1504901738750/sulo_vod/playlist.m3u8")
    (vod-thumbnail-url [_ _ _]
      "https://vods.sulo.live/wowza/17592186058863/1504224000/sulo_vod/playlist.jpg")))


(defmethod shared/shared-component [:shared/live-stream :env/prod]
  [reconciler _ _]
  (let [wowza-subscriber-url (client-env/get-key (shared/by-key reconciler :shared/client-env) :wowza-subscriber-url)]
    (reify ILiveStreamProvider
      (live-stream-url [this store-id]
        (str wowza-subscriber-url "/live/" store-id "/playlist.m3u8"))
      (live-stream-thumbnail-url [this store-id size]
        (str wowza-subscriber-url
             "/thumbnail?application=live&format=jpg&streamname=" store-id
             "&size=" (condp = size
                        ::small "640x360"
                        ::large "1280x720"))))))

(defmethod shared/shared-component [:shared/live-stream :env/dev]
  [reconciler _ _]
  (let [dev-vods (shared/by-key reconciler :shared/vods)]
    (reify ILiveStreamProvider
      (live-stream-url [_ store-id]
        (vod-url dev-vods store-id nil))
      (live-stream-thumbnail-url [_ store-id _]
        (vod-thumbnail-url dev-vods store-id nil)))))
