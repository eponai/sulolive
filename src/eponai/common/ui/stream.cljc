(ns eponai.common.ui.stream
  (:require
    [clojure.set :as set]
    [eponai.client.parser.message :as msg]
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

(def fake-messages [{:photo "/assets/img/kids-new.jpg"
                     :text  "this is some message"
                     :user  "Seeley B"
                     }
                    {:photo "/assets/img/men-new.jpg"
                     :text  "Hey there I was wondering something"
                     :user  "Rick"
                     }
                    {:photo "/assets/img/women-new.jpg"
                     :user  "Diana Gren"
                     :text  "Oh yeah mee too, I was wondering how really long messages would show up in the chat list. I mean it could look really really ugly worst case..."}
                    ])

(def fake-photos (mapv :photo fake-messages))

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
#?(:cljs
   (defn video-element []
     (first (utils/elements-by-tagname "video"))))

(defn get-messages [component]
  (let [messages (get-in (om/props component) [:query/chat :chat/messages])
        {client-side true server-side false} (group-by (comp true? :chat.message/client-side-message?) messages)]
    (concat server-side client-side)))

(defui Stream
  static om/IQuery
  (query [this]
    [:query/messages
     {:query/auth [:db/id]}
     {:query/store [:db/id]}
     {:query/stream-config [:ui.singleton.stream-config/subscriber-url]}
     {:query/chat [:chat/store
                   ;; ex chat modes: :chat.mode/public :chat.mode/sub-only :chat.mode/fb-authed :chat.mode/owner-only
                   :chat/modes
                   {:chat/messages [:chat.message/client-side-message?
                                    {:chat.message/user [:user/email]}
                                    :chat.message/text
                                    :chat.message/timestamp]}]}])
  Object
  #?(:cljs
     (server-url [this]
                 (get-in (om/props this) [:query/stream-config :ui.singleton.stream-config/subscriber-url])))
  #?(:cljs
     (subscribe-hls [this]
                    (if-let [server-url (.server-url this)]
                      (let [video (.getElementById js/document "sulo-wowza")
                            hls (js/Hls.)
                            store (:query/store (om/props this))
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
  #?(:cljs (ensure-hls-subscription [this]
                                    (when (nil? (:hls (om/get-state this)))
                                      (.subscribe-hls this))))
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
    #?(:cljs
      (let [{:keys [hls]} (om/get-state this)]
        (when hls
          (.destroy hls)))))
  (componentDidUpdate [this prev-props prev-state]
    #?(:cljs
       (.ensure-hls-subscription this)))
  (componentDidMount [this]
    #?(:cljs
       (.ensure-hls-subscription this))

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
  (initLocalState [_]
    {:show-chat?  true
     :fullscreen? false
     :playing? true})

  #?(:cljs
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
               (om/update-state! this assoc :playing? false)))))))
  #?(:cljs
     (toggle-fullscreen
       [this]
       (let [fullscreen? (some? (utils/fullscreen-element))
             v (video-element)]
         (when v
           (if fullscreen?
             (utils/exit-fullscreen)
             (utils/request-fullscreen v))
           (om/update-state! this assoc :fullscreen? (not fullscreen?))))))
  (render [this]
    (let [{:keys [show-chat? fullscreen? playing? chat-message]} (om/get-state this)
          {:keys [stream-name]} (om/get-computed this)
          {:query/keys [store]} (om/props this)
          messages (get-messages this)]
      (dom/div #js {:id "sulo-video-container" :className (str "flex-video widescreen "
                                                               (when show-chat? "sulo-show-chat")
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
        (my-dom/div
          (cond->> (css/add-class ::css/stream-chat-container)
                   show-chat?
                   (css/add-class :show))
          (dom/a #js {:className "button show-button"
                      :onClick   #(om/update-state! this assoc :show-chat? true)} (dom/i #js {:className "fa fa-comments fa-fw"}))
          (my-dom/div
            nil
            (dom/a #js {:className "button hollow secondary hide-button"
                        :onClick   #(om/update-state! this assoc :show-chat? false)}
                   (dom/i #js {:className "fa fa-chevron-right fa-fw"})))

          ;(menu/horizontal nil
          ;                 (menu/item nil
          ;                            (dom/a nil "Chat")))
          (dom/div #js {:className "content"}
            (menu/vertical
              #?(:cljs
                      (css/add-class :messages-list {:onMouseOver #(set! js/document.body.style.overflow "hidden")
                                                     :onMouseOut  #(set! js/document.body.style.overflow "scroll")})
                 :clj (css/add-class :messages-list))
              (map (fn [msg]
                     (menu/item (css/add-class :message-container)
                                (my-dom/div
                                  (->> (css/grid-row)
                                       (css/align :top))
                                  (my-dom/div (->> (css/grid-column)
                                                   (css/grid-column-size {:small 2}))
                                              (photo/circle {:src (nth fake-photos
                                                                       (dec (mod (:chat.message/user msg)
                                                                                 (count fake-photos)))
                                                                       (first fake-photos))}))
                                  (my-dom/div (css/grid-column)
                                              (dom/small nil
                                                         (dom/strong nil (str (get-in msg [:chat.message/user :user/email])
                                                                              ": "))
                                                         (dom/span nil (:chat.message/text msg)))))))
                   messages))
            (dom/div #js {:className "input-container"}
              (my-dom/div
                (css/grid-row)
                (my-dom/div (css/grid-column)
                            (dom/input #js {:className   ""
                                            :type        "text"
                                            :placeholder "Your message..."
                                            :value       (or chat-message "")
                                            :onChange    #(om/update-state! this assoc :chat-message (.-value (.-target %)))}))
                (my-dom/div (->> (css/grid-column)
                                 (css/add-class :shrink))
                            (dom/a #js {:className "button green small"
                                        :onClick   #(do
                                                      (if (:query/auth (om/props this))
                                                        (do (om/transact! this `[(chat/send-message
                                                                                   ~{:store (select-keys store [:db/id])
                                                                                     :text  chat-message})
                                                                                 :query/chat])
                                                            (om/update-state! this assoc :chat-message ""))
                                                        #?(:cljs (js/alert "Log in to send chat messages"))))}
                                   (dom/span nil "Send")))))))))))

(def ->Stream (om/factory Stream))
