(ns eponai.common.ui.stream
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.shared :as shared]
    [taoensso.timbre :refer [debug error info]]
    [eponai.web.ui.stream.videoplayer :as video]
    #?(:cljs [eponai.web.modules :as modules])
    [eponai.common.ui.elements.css :as css]
    [eponai.common.database :as db]
    [eponai.client.videos :as videos]
    [eponai.client.client-env :as client-env]))

(defn add-fullscreen-listener [f]
  #?(:cljs
     (doseq [prefix [nil "moz" "webkit"]]
       (.addEventListener js/document (str prefix "fullscreenchange") f))))

(defn remove-fullscreen-listener [f]
  #?(:cljs
     (doseq [prefix [nil "moz" "webkit"]]
       (.removeEventListener js/document (str prefix "fullscreenchange") f))))

(defui Stream
  ;static om/IQuery
  ;(query [this]
  ;  [{:query/stream [:stream/store :stream/state]}
  ;   {:query/stream-config [:ui.singleton.stream-config/subscriber-url]}])

  Object
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [on-fullscreen-change]} (om/get-state this)]
         (debug "Stream will unmount")
         (remove-fullscreen-listener on-fullscreen-change))))
  (componentDidMount [this]
    #?(:cljs
       (let [{:keys [on-fullscreen-change]} (om/get-state this)]
         (add-fullscreen-listener on-fullscreen-change))))

  (initLocalState [this]
    (let [{:keys [on-fullscreen-change]} (om/get-computed this)]
      {:fullscreen?          false
       :playing?             true
       :on-fullscreen-change #(let [{:keys [fullscreen?]} (om/get-state this)]
                               (om/update-state! this update :fullscreen? not)
                               (when on-fullscreen-change
                                 (on-fullscreen-change (not fullscreen?))))}))

  (render [this]
    (let [{:keys [stream stream-config]} (om/props this)
          {:keys [widescreen? store vod-timestamp]} (om/get-computed this)
          live-stream (shared/by-key this :shared/live-stream)
          store-id (:db/id store)
          stream-url (videos/live-stream-url live-stream store-id)
          stream-thumbnail (videos/live-stream-thumbnail-url live-stream store-id ::videos/large)

          vods (shared/by-key this :shared/vods)
          vod-url (when (some? vod-timestamp)
                    (videos/vod-url vods
                                    store-id
                                    vod-timestamp))
          ;stream-url "http://wms.shared.streamshow.it/carinatv/carinatv/playlist.m3u8"
          {:stream/keys [title]} stream]
      (debug "VOD URL: " vod-url)
      (debug "large-live-thumbnail: " stream-thumbnail)
      (dom/div
        {:id "sulo-video-container" :classes [(str "flex-video"
                                                   (when widescreen? " widescreen"))]}
        (dom/div {:classes ["sulo-spinner-container"]}
                 (dom/span {:classes ["sl-loading-signal"]}))
        (video/->VideoPlayer (om/computed {:source (or vod-url stream-url)
                                           :poster (if (some? vod-url)
                                                     (videos/vod-thumbnail-url vods (:db/id store) vod-timestamp)
                                                     stream-thumbnail)}
                                          {:is-live? (not vod-url)}))))))

;(def Stream (script-loader/js-loader {:component Stream-no-loader
;                                      #?@(:cljs [:scripts [[#(exists? js/WowzaPlayer)
;                                                            "//player.wowza.com/player/1.0.10.4565/wowzaplayer.min.js"]]])}))
(def ->Stream (om/factory Stream))

#?(:cljs
   (modules/set-loaded! :stream+chat))
