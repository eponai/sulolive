(ns eponai.common.ui.stream
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
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

(defui Stream
  static om/IQuery
  (query [this]
    [:query/current-route])
  Object
  #?(:cljs
     (server-url [this]
                 (get-in (om/props this) [:query/stream-config :ui.singleton.stream-config/hostname])))
  ;#?(:cljs
  ;   (subscribe-hls [this]
  ;                   (let [video (.getElementById js/document "video")
  ;                         hls (js/Hls.)
  ;                         store-id (get-in (om/props this) [:query/current-route :route-params :store-id])]
  ;                     (debug "STREAM STORE ID: " store-id)
  ;                     (.loadSource hls (str "http://localhost:1935/live/" store-id "/playlist.m3u8"))
  ;                     (.attachMedia hls video)
  ;                     (.on hls js/Hls.Events.MANIFEST_PARSED (fn []
  ;                                                              (debug "IN MANIFEST PARSED. PLAYING!?")
  ;                                                              (.play video))))))
  (componentDidMount [this]
    #?(:cljs
       (do
         (set! (.-key js/jwplayer) "UO97+AUXyaO4JyC16F+PtgrCT4+YBDrgXWTdYQ==")
         (let [player (js/jwplayer "sulo-wowza")]
           (.setup
             player
             (clj->js {:playlist "//content.jwplatform.com/feeds/F2YKWOWd.rss"
                       :controls {:enableFullScreen true}
                       :aspectratio "16:9"
                       :stretching "uniform"
                       :width "100%"}))
           (.on player "fullscreen"
                (fn [e]
                  (debug "I am now fullscreen: " (.-fullscreen e))
                  (om/update-state! this assoc :fullscreen? (.-fullscreen e))))))
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
       ))
  (initLocalState [_]
    {:show-chat? true})

  (render [this]
    (let [{:keys [show-chat? fullscreen?]} (om/get-state this)
          {:keys [stream-name]} (om/get-computed this)
          messages [{:photo "/assets/img/collection-women.jpg"
                     :text  "this is some message"
                     :user  "Diana Gren"
                     }
                    {:photo "/assets/img/collection-men.jpg"
                     :text  "Hey there I was wondering something"
                     :user  "Rick"
                     }
                    {:photo "/assets/img/collection-women.jpg"
                     :user  "Diana Gren"
                     :text  "Oh yeah mee too, I was wondering how really long messages would show up in the chat list. I mean it could look really really ugly worst case..."}
                    {:photo "/assets/img/collection-women.jpg"
                     :user  "Diana Gren"
                     :text  "Oh yeah mee too, I was wondering how really long messages would show up in the chat list. I mean it could look really really ugly worst case..."}
                    {:photo "/assets/img/collection-women.jpg"
                     :user  "Diana Gren"
                     :text  "Oh yeah mee too, I was wondering how really long messages would show up in the chat list. I mean it could look really really ugly worst case..."}
                    {:photo "/assets/img/collection-women.jpg"
                     :user  "Diana Gren"
                     :text  "Oh yeah mee too, I was wondering how really long messages would show up in the chat list. I mean it could look really really ugly worst case..."}]]
      (debug "STREAM PROPS:" (om/props this))
      (dom/div #js {:id "sulo-video-container" :className (str "flex-video widescreen "
                                                               (when show-chat? "sulo-show-chat")
                                                               (when fullscreen? " fullscreen"))}
        ;(dom/div #js {:id "sulo-video"})
        (dom/div #js {:id "sulo-wowza"})
        ;(dom/video #js {:id "video"}
        ;           ;#?(:cljs
        ;           ;   (dom/source #js {:src  (str "http://" (.server-url this) ":5080/live/" (url->store-id) ".m3u8")
        ;           ;                    :type "application/x-mpegURL"}))
        ;           )
        (dom/div #js {:className "stream-title-container"}
          (dom/span nil stream-name))
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
                                              (photo/circle {:src (:photo msg)}))
                                  (my-dom/div (css/grid-column)
                                              (dom/small nil
                                                         (dom/strong nil (str (:user msg) ": "))
                                                         (dom/span nil (:text msg)))))))
                   messages))
            (dom/div #js {:className "input-container"}
              (my-dom/div
                (css/grid-row)
                (my-dom/div (css/grid-column)
                            (dom/input #js {:className   ""
                                            :type        "text"
                                            :placeholder "Your message..."}))
                (my-dom/div (->> (css/grid-column)
                                 (css/add-class :shrink))
                            (dom/a #js {:className "button green small"}
                                   (dom/span nil "Send")))))))))))

(def ->Stream (om/factory Stream))
