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
     (subscribe-hls [this]
                     (let [video (.getElementById js/document "video")
                           hls (js/Hls.)
                           store-id (get-in (om/props this) [:query/current-route :route-params :store-id])]
                       (debug "STREAM STORE ID: " store-id)
                       (.loadSource hls (str "http://localhost:1935/live/" store-id "/playlist.m3u8"))
                       (.attachMedia hls video)
                       (.on hls js/Hls.Events.MANIFEST_PARSED (fn []
                                                                (debug "IN MANIFEST PARSED. PLAYING!?")
                                                                (.play video))))))
  #?(:cljs
     (componentDidMount [this]
                        ;(.publish this)
                        ;; THIS ONE IS THE REAL ONE:
                        ;; (.subscribe-hls this)
                        ;(let [player (js/videojs "red5pro-subscriber")]
                        ;    (.play player))
                        )
     )
  (render [this]
    (let [{:keys [show-chat?]} (om/get-state this)
          {:keys [stream-name]} (om/get-computed this)
          messages [{:photo "/assets/img/collection-women.jpg"
                     :text "this is some message"
                     :user "Diana Gren"
                     }
                    {:photo "/assets/img/collection-men.jpg"
                     :text "Hey there I was wondering something"
                     :user "Rick"
                     }
                    {:photo "/assets/img/collection-women.jpg"
                     :user "Diana Gren"
                     :text "Oh yeah mee too"}]]
      (debug "STREAM PROPS:" (om/props this))
      (dom/div #js {:id "sulo-video-container"}
        (dom/div #js {:id "sulo-video" :className "flex-video widescreen"}
          (my-dom/div
            (cond->> (css/add-class ::css/stream-chat-container)
                     show-chat?
                     (css/add-class :show))
            (my-dom/div
              (->> {:onClick #(om/update-state! this assoc :show-chat? (not show-chat?))}
                   (css/add-class ::css/stream-toggle))
              (my-dom/a (cond-> (->> (css/add-class ::button)
                                     (css/add-class :expanded))
                                show-chat?
                                (->> (css/add-class :secondary)
                                     (css/add-class :hollow)))
                        (if show-chat?
                          (dom/span nil ">>")
                          (dom/i #js {:className "fa fa-comments fa-fw"}))))
            (dom/div #js {:className "stream-chat-content"}
              (dom/span nil "This is a message"))
            (dom/div #js {:className "stream-chat-input"}
              (dom/input #js {:type        "text"
                              :placeholder "Your message..."})
              (dom/a #js {:className "button expanded green"}
                     (dom/span nil "Send"))))
          (dom/video #js {:id "video"}
                     ;#?(:cljs
                     ;   (dom/source #js {:src  (str "http://" (.server-url this) ":5080/live/" (url->store-id) ".m3u8")
                     ;                    :type "application/x-mpegURL"}))
                     )
          (dom/div #js {:className "stream-title-container"}
            (dom/span nil stream-name)
            )
          (my-dom/div
            (cond->> (css/add-class ::css/stream-chat-container)
                     show-chat?
                     (css/add-class :show))
            (dom/div #js {:className "content"}
              (my-dom/div
                (->> {:onClick #(om/update-state! this assoc :show-chat? (not show-chat?))}
                     (css/add-class ::css/stream-toggle))
                (my-dom/a (cond-> (css/add-class ::button)
                                  show-chat?
                                  (->> (css/add-class :secondary)
                                       (css/add-class :hollow)))
                          (if show-chat?
                            (dom/span nil "Hide >>")
                            (dom/i #js {:className "fa fa-comments fa-fw"}))))
              (menu/vertical
                (css/add-class :messages-list)
                (map (fn [msg]
                       (menu/item (css/add-class :message-container)
                                  (my-dom/div
                                    (->> (css/grid-row)
                                         (css/align :middle))
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
                  (my-dom/div
                    (->> (css/grid-column)
                         (css/grid-column-size {:small 10}))
                    (dom/input #js {:className   ""
                                    :type        "text"
                                    :placeholder "Your message..."}))
                  (my-dom/div (->> (css/grid-column)
                                   (css/grid-column-size {:small 2}))
                              (dom/a #js {:className "button expanded green small"}
                                     (dom/span nil "Send"))))))))))))

(def ->Stream (om/factory Stream))
