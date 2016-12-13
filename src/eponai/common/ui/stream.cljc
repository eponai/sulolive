(ns eponai.common.ui.stream
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :as timbre :refer [debug error ]]))

#?(:cljs
   (defn video-config []
     (let [protocol js/window.location.protocol
           is-secure? (.charAt protocol (- (.-length protocol) 2))
           common {:swf               "/lib/red5pro/red5pro-subscriber.swf"
                   :swfobjectURL      "/lib/swfobject/swfobject.js"
                   :productInstallURL "/lib/swfobject/playerProductInstall.swf"
                   :host              "192.168.0.34"
                   :app               "live"
                   :streamName        "petter"
                   :iceServers        [{:urls "stun:stun2.l.google.com:19302"}]}]
       {:rtc  (assoc common :protocol (if is-secure? "wss" "ws")
                            :port (if is-secure? 8083 8081)
                            :subscriptionId (int (rand-int 10000)))
        :rtmp (assoc common :protocol "rtmp"
                            :port 1935
                            :mimeType "rtmp/flv"
                            :useVideoJS false)
        :hls  (assoc common :protocol "http"
                            :port 5080
                            :mimeType "application/x-mpegURL")})))

(defui Stream
  Object
  #?(:cljs
     (subscribe [this]
                (let [subscriber (new js/red5prosdk.Red5ProSubscriber)
                      view (new js/red5prosdk.PlaybackView "red5pro-subscriber")
                      _ (.attachSubscriber view subscriber)
                      conf (video-config)]
                  (debug "subscriber: " subscriber " view " view)
                  (debug (clj->js conf))
                  ;;(.on subscriber "*" js/window.red5proHandleSubscriberEvent)
                  (.. subscriber
                      (setPlaybackOrder #js ["hls" "rtc" "rtmp"])
                      (init (clj->js conf))
                      (then (fn [sub]
                              (debug "playing subscriber: " sub)
                              (.play sub)
                              ;;(.off subscriber "*" js/window.red5proHandleSubscriberEvent)
                              ))
                      (catch (fn [e]
                               (error "Subscribe error: " e))))
                  subscriber)))
  #?(:cljs
     (componentDidMount [this]
                        ;(.publish this)
                        ;(let [player (js/videojs "red5pro-subscriber")]
                        ;  (.play player))
                        ;(.subscribe this)
                        )
     )
  (render [this]
    (dom/div #js {:id "sulo-video-container"}
      (dom/div #js {:id "sulo-video"}
        (dom/video #js {:id "red5pro-subscriber" :controls true :className "video-element video-js vjs-sublime-skin"}
                   (dom/source #js {:src  "http://192.168.0.34:5080/live/petter.m3u8"
                                    :type "application/x-mpegURL"}))))))

(def ->Stream (om/factory Stream))
