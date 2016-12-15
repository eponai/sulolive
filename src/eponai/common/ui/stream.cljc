(ns eponai.common.ui.stream
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :as timbre :refer [debug error info]]))

#?(:cljs
   (defn url->store-id []
     ;; TODO: This logic is copied from store.cljc
     ;;       Use some library, routing or whatever
     ;;       to get the store-id.
     ;;       We're using the store-id for the stream name for now.
     (let [path js/window.location.pathname]
       (last (clojure.string/split path #"/")))))

(defn url-protocol [url]
  (->> (seq url)
       (take-while #(not= \: %))
       (apply str)))

#?(:cljs
   (defn video-config [server-url]
     (let [is-secure? (= "https" (url-protocol server-url))
           common {:swf               "/lib/red5pro/red5pro-subscriber.swf"
                   :swfobjectURL      "/lib/swfobject/swfobject.js"
                   :productInstallURL "/lib/swfobject/playerProductInstall.swf"
                   :host              server-url
                   :app               "live"
                   :streamName        (url->store-id)
                   :iceServers        [{:urls "stun:stun2.l.google.com:19302"}]}]
       (info "Subscribing to stream-name " (:streamName common))
       {:rtc  (assoc common :protocol (if is-secure? "wss" "ws")
                            :port (if is-secure? 8083 8081)
                            :subscriptionId (str (int (rand-int 10000))))
        :rtmp (assoc common :protocol "rtmp"
                            :port 1935
                            :mimeType "rtmp/flv"
                            :useVideoJS false)
        :hls  (assoc common :protocol "http"
                            :port 5080
                            :mimeType "application/x-mpegURL")})))

(defui Stream
  static om/IQuery
  (query [this]
    [{:query/stream-config [:ui.singleton.stream-config/hostname]}])
  Object
  #?(:cljs
     (server-url [this]
                 (get-in (om/props this) [:query/stream-config :ui.singleton.stream-config/hostname])))
  #?(:cljs
     (subscribe [this]
                (let [subscriber (new js/red5prosdk.Red5ProSubscriber)
                      view (new js/red5prosdk.PlaybackView "red5pro-subscriber")
                      _ (.attachSubscriber view subscriber)
                      server-url (.server-url this)
                      conf (video-config server-url)]
                  (debug "subscriber: " subscriber " view " view)
                  (debug (clj->js conf))
                  ;;(.on subscriber "*" js/window.red5proHandleSubscriberEvent)
                  (.. subscriber
                      (setPlaybackOrder #js ["rtc"])
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
                        (.subscribe this)
                        ;(let [player (js/videojs "red5pro-subscriber")]
                        ;    (.play player))
                        )
     )
  (render [this]
    (debug "STREAM PROPS:" (om/props this))
    (dom/div #js {:id "sulo-video-container"}
      (dom/div #js {:id "sulo-video" :className "flex-video widescreen"}
        (dom/video #js {:id "red5pro-subscriber" :controls true :className "video-element video-js vjs-sublime-skin"}
                   ;#?(:cljs
                   ;   (dom/source #js {:src  (str "http://" (.server-url this) ":5080/live/" (url->store-id) ".m3u8")
                   ;                    :type "application/x-mpegURL"}))
                   )))))

(def ->Stream (om/factory Stream))
