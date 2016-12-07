(ns eponai.common.ui.stream
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(defui Stream
  Object
  #?(:cljs
     (subscribe [this]
                (let [subscriber (new js/red5prosdk.Red5ProSubscriber)
                      view (new js/red5prosdk.PlaybackView "red5pro-subscriber")]
                  (.. view
                      (attachSubscriber subscriber))
                  (let [protocol js/window.location.protocol
                        is-secure (.charAt protocol (- (.-length protocol) 2))
                        ice-servers #js [#js {:urls "stun:stun2.l.google.com:19302"}]]
                    (debug "subscriber: " subscriber " view " view)
                    (.on subscriber "*" js/window.red5proHandleSubscriberEvent)
                    (.. subscriber
                        (setPlaybackOrder #js ["hls"])
                        (init
                          #js {
                               :rtmp #js {:protocol          "rtmp" ;(if is-secure "wss" "ws")
                                          :host              "192.168.0.32"
                                          :port              1935 ;(if is-secure "8083" "8081")
                                          :width             "1000px"
                                          :height            "750px"
                                          :mimeType          "rtmp/flv"
                                          :useVideoJS        false
                                          :swf               "/lib/red5pro/red5pro-subscriber.swf"
                                          :swfobjectURL      "/lib/swfobject/swfobject.js"
                                          :productInstallURL "/lib/swfobject/playerProductInstall.swf"
                                          :app               "live"
                                          :streamName        "mystream"}
                               :hls  #js {:protocol          "http"
                                          :port              5080
                                          :streamName        "mystream"
                                          :mimeType          "application/x-mpegURL"
                                          :swf               "/lib/red5pro/red5pro-video-js.swf"
                                          :swfobjectURL      "/lib/swfobject/swfobject.js"
                                          :productInstallURL "/lib/swfobject/playerProductInstall.swf"}})
                        (then (fn []
                                (.off subscriber "*" js/window.red5proHandleSubscriberEvent)
                                ))
                        (catch (fn [error]
                                 (debug "Subscribe error: " error))))
                    subscriber))))
  #?(:cljs
     (componentDidMount [this]
                        ;(.publish this)
                        ;(.subscribe this)
                        ;(let [player (js/videojs "red5pro-subscriber")]
                        ;  (.play player))
                        )
     )
  (render [this]
    (dom/div #js {:id "video-container"}
      (dom/video #js {:id "red5pro-subscriber" :className "video-js vjs-sublime-skin"}))))

(def ->Stream (om/factory Stream))