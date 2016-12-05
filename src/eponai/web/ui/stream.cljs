(ns eponai.web.ui.stream
  (:require
    [goog.dom :as gdom]
    [om.next :as om :refer [defui]]
    [sablono.core :refer [html]]
    [taoensso.timbre :refer [debug]]
    [eponai.client.utils :as utils]
    [eponai.common.parser :as parser]
    [eponai.client.backend :as backend]
    [eponai.client.remotes :as remotes]
    [eponai.client.parser.merge :as merge]))

(defui VideoApp
  Object
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
                   :rtmp #js {:protocol          "rtmp"     ;(if is-secure "wss" "ws")
                              :host              "192.168.0.32"
                              :port              1935       ;(if is-secure "8083" "8081")
                              :width             "1000px"
                              :height            "750px"
                              :mimeType          "rtmp/flv"
                              :useVideoJS        true
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
        subscriber
        )))
  (componentDidMount [this]
    ;(.publish this)
    (.subscribe this)
    (let [player (js/videojs "red5pro-subscriber")]
      (.play player))
    )
  (render [this]
    (html
      [:div {:id "video-container"
             :style {:height "100%"
                     :width "75%"}}
       [:video {:id "red5pro-subscriber" :class "video-js vjs-sublime-skin"}]
       ])))

(defn run []
  (debug "Run video")
  (let [conn (utils/init-conn)
        reconciler (om/reconciler {:state   conn
                                   :parser  (parser/client-parser)
                                   :remotes []
                                   :migrate nil})]
    (reset! utils/reconciler-atom reconciler)
    (om/add-root! reconciler VideoApp (gdom/getElement "stream-container"))))

