(ns eponai.web.ui.stream.videoplayer
  (:require
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [taoensso.timbre :refer [debug info error]]))

(def video-element-id "sl-video-player")
(def source-element-id "sl-video-player-source")

(defui VideoPlayer

  Object
  (hls-supported? [_]
    #?(:cljs (boolean (.isSupported js/Hls))
       :clj false))

  (subscribe-hls [this]
    #?(:cljs
       (let [video-element (utils/element-by-id video-element-id)
             {:keys [source]} (om/props this)
             {:keys [hljsjs]} (om/get-state this)
             hls (new js/Hls)]
         (when (some? hljsjs)
           (.destroy hljsjs))
         (.attachMedia hls video-element)
         (.on hls
              js/Hls.Events.MEDIA_ATTACHED
              (fn []
                (info "HLS: Media attached")
                (.loadSource hls source)
                (.on hls
                     js/Hls.Events.MANIFEST_PARSED
                     (fn []
                       (info "HLS: Manifest parsed")
                       (.play video-element)))))
         (.on hls
              js/Hls.Events.ERROR
              (fn [event data]
                (let [is-fatal? (.-fatal data)
                      type (.-type data)]
                  (error "HLS: Error loading media of type " type ", is fatal " is-fatal?)
                  (when is-fatal?
                    (cond (= type js/Hls.ErrorTypes.NETWORK_ERROR)
                          (.startLoad hls)
                          (= type js/Hls.ErrorTypes.MEDIA_ERROR)
                          (.recoverMediaError hls)
                          :else
                          (.destroy hls))))))

         (om/update-state! this assoc :hlsjs hls))))

  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [hlsjs]} (om/get-state this)]
         (.destroy hlsjs))))

  (componentDidMount [this]
    #?(:cljs
       (let [video-element (utils/element-by-id video-element-id)]
         (when (.hls-supported? this)
           (.subscribe-hls this))

         (.setup js/plyr video-element (clj->js {:controls ["play-large"
                                                            "play"
                                                            ;"progress"
                                                            ;"current-time"
                                                            "mute"
                                                            "volume"
                                                            ;"captions"
                                                            "fullscreen"]})))))

  (render [this]
    (let [{:keys [source]} (om/props this)]
      (dom/video
        {:id video-element-id}
        (dom/source
          (cond-> {:id source-element-id}
                  (not (.hls-supported? this))
                  (assoc :src source)))))))

(def ->VideoPlayer (om/factory VideoPlayer))