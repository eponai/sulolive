(ns eponai.client.vods
  (:require [eponai.common.shared :as shared]))

(defprotocol IVodProvider
  (vod-url [this store-id timestamp] "Returns a vod url given a store and the start time of the vod."))

(defmethod shared/shared-component [:shared/vods :env/prod]
  [_ _ _]
  (reify IVodProvider
    (vod-url [this store-id timestamp]
      (str "https://vods.sulo.live/wowza/" store-id "/" timestamp "/sulo_vod/playlist.m3u8"))))

(defmethod shared/shared-component [:shared/vods :env/dev]
  [_ _ _]
  ;; For every store and timestamp, just return the same url to fake it.
  (reify IVodProvider
    (vod-url [_ _ _]
      "https://vods.sulo.live/wowza/17592186045420/1504628739877/sulo_vod/playlist.m3u8")))
