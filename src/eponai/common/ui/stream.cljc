(ns eponai.common.ui.stream
  (:require
    [clojure.set :as set]
    [eponai.client.parser.message :as msg]
    [eponai.client.chat :as client.chat]
    [eponai.common.stream :as stream]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs [eponai.web.utils :as utils])
    [taoensso.timbre :as timbre :refer [debug error info]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]))


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

(defn video-element []
  #?(:cljs (first (utils/elements-by-tagname "video"))))


(defui Stream
  static om/IQuery
  (query [this]
    [:query/messages
     {:query/stream-config [:ui.singleton.stream-config/subscriber-url]}])
  client.chat/IStoreChatListener
  (start-listening! [this store-id]
    (debug "Will start listening to store-id: " store-id)
    (client.chat/start-listening! (:shared/store-chat-listener (om/shared this)) store-id))
  (stop-listening! [this store-id]
    (client.chat/stop-listening! (:shared/store-chat-listener (om/shared this)) store-id))

  Object
  (server-url [this]
    (get-in (om/props this) [:query/stream-config :ui.singleton.stream-config/subscriber-url]))

  (subscribe-hls [this]
    #?(:cljs (if-let [server-url (.server-url this)]
               (let [video (.getElementById js/document "sulo-wowza")
                     hls (js/Hls.)
                     store (:store (om/get-computed this))
                     stream-name (stream/stream-name store)
                     stream-url (stream/wowza-live-stream-url server-url stream-name)]
                 (om/update-state! this assoc :hls hls)
                 (debug "Stream url: " stream-url)
                 (.loadSource hls stream-url)
                 (.attachMedia hls video)
                 (.on hls js/Hls.Events.MANIFEST_PARSED (fn []
                                                          (debug "HLS manifest parsed. Will play hls.")
                                                          (.play video)
                                                          (om/update-state! this assoc :playing? true)))
                 (.on hls js/Hls.Events.ERROR (fn [e d]
                                                (let [error-type (.-type d)]
                                                  (error "HLS Error: " e " type: " error-type " data: " d)
                                                  (comment
                                                    "Would start the cartoon, but turned off because it's irratating."
                                                    (when (= js/Hls.ErrorTypes.NETWORK_ERROR error-type)
                                                      (.detachMedia hls)
                                                      (.loadSource hls "http://www.streambox.fr/playlists/x36xhzz/x36xhzz.m3u8")
                                                      (.attachMedia hls video)))))))
               (debug "Hasn't received server-url yet. Needs server-url to start stream."))))
  (ensure-hls-subscription [this]
    (when (nil? (:hls (om/get-state this)))
      (.subscribe-hls this)))
  ;#?(:cljs
  ;   (subscribe-jw-player
  ;     [this]
  ;     (set! (.-key js/jwplayer) "UO97+AUXyaO4JyC16F+PtgrCT4+YBDrgXWTdYQ==")
  ;     (let [player (js/jwplayer "sulo-wowza")]
  ;       (.setup
  ;         player
  ;         (clj->js {:file        "https://www.youtube.com/watch?v=FAJaQbWvXEY"
  ;                   :controls    {:enableFullScreen true}
  ;                   :aspectratio "16:9"
  ;                   :stretching  "uniform"
  ;                   :width       "100%"}))
  ;       (.on player "fullscreen"
  ;            (fn [e]
  ;              (debug "I am now fullscreen: " (.-fullscreen e))
  ;              (om/update-state! this assoc :fullscreen? (.-fullscreen e)))))))
  (componentWillUnmount [this]
    (let [{:keys [hls]} (om/get-state this)]
      (when hls
        (.destroy hls)))
    (client.chat/stop-listening! this (:db/id (:query/store (om/props this)))))
  (componentDidUpdate [this prev-props prev-state]
    (.ensure-hls-subscription this)
    (let [old-store (:db/id (:query/store prev-props))
          new-store (:db/id (:query/store (om/props this)))]
      (when (not= old-store new-store)
        (client.chat/stop-listening! this old-store)
        (client.chat/start-listening! this new-store))))
  (componentDidMount [this]
    (.ensure-hls-subscription this)
    (client.chat/start-listening! this (:db/id (:query/store (om/props this))))

    ;(js/WowzaPlayer.create "sulo-wowza"
    ;                       (clj->js {:license   "PLAY1-aaEJk-4mGcn-jW3Yr-Fxaab-PAYm4"
    ;                                 :title     "THis is some crazy title!"
    ;                                 ;"description"          "",
    ;                                 :sourceURL "http://www.streambox.fr/playlists/x36xhzz/x36xhzz.m3u8"
    ;                                 :autoPlay  true
    ;                                 :volume "75"
    ;                                 :uiShowDurationVsTimeRemaining true
    ;                                 ;"mute"                 false,
    ;                                 ;"loop"                 false,
    ;                                 ;"audioOnly"            false,
    ;                                 ;"uiShowQuickRewind"    true,
    ;                                 ;"uiQuickRewindSeconds" "30"
    ;                                 }))
    )
  (initLocalState [this]
    (let [{:keys [hide-chat?]} (om/get-computed this)]
      {:show-chat?  (not hide-chat?)
       :fullscreen? false
       :playing?    true}))

  (toggle-play
    [this]
    (let [v (video-element)
          is-paused? (.-paused v)]
      (when v
        (if is-paused?
          (do
            (.play v)
            (om/update-state! this assoc :playing? true))
          (do
            (.pause v)
            (om/update-state! this assoc :playing? false))))))

  (toggle-fullscreen
    [this]
    #?(:cljs
       (let [fullscreen? (some? (utils/fullscreen-element))
             v (video-element)]
         (when v
           (if fullscreen?
             (utils/exit-fullscreen)
             (utils/request-fullscreen v))
           (om/update-state! this assoc :fullscreen? (not fullscreen?))))))
  (render [this]
    (let [{:keys [show-chat? fullscreen? playing?]} (om/get-state this)
          {:keys [stream-name widescreen?]} (om/get-computed this)

          ]
      (dom/div #js {:id "sulo-video-container" :className (str "flex-video"
                                                               (when widescreen? " widescreen")
                                                               (when fullscreen? " fullscreen"))}
        ;(common/loading-spinner)
        (dom/div #js {:className "sulo-spinner-container"}
          (dom/i #js {:className "fa fa-spinner fa-spin fa-4x"}))
        ;(dom/div #js {:id "sulo-video"})
        (dom/video #js {:id "sulo-wowza"})
        (dom/div #js {:id "video-controls"}
          (dom/div nil
            (dom/a #js {:className "button large"
                        :onClick #(.toggle-play this)}
                   (dom/i #js {:className (str "fa fa-fw " (if playing? "fa-pause" "fa-play"))}))
            ;(dom/input #js {:id "video-range"
            ;                :type "range" :value 0})
            (dom/a #js {:id        "video-mute"
                        :className "button large"}
                   (dom/i #js {:className "fa fa-volume-off fa-fw"})))
          (dom/a #js {:id        "video-fullscreen"
                      :className "button large"
                      :onClick   #(.toggle-fullscreen this)}
                 (dom/i #js {:className "fa fa-expand fa-fw"})))
        ;(dom/video #js {:id "video"}
        ;           ;#?(:cljs
        ;           ;   (dom/source #js {:src  (str "http://" (.server-url this) ":5080/live/" (url->store-id) ".m3u8")
        ;           ;                    :type "application/x-mpegURL"}))
        ;           )
        ;(dom/div #js {:className "stream-title-container"}
        ;  (dom/p nil stream-name))
        ))))

(def ->Stream (om/factory Stream))
