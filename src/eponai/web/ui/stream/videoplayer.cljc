(ns eponai.web.ui.stream.videoplayer
  (:require
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [eponai.web.social :as social]
    [eponai.common.ui.dom :as dom]
    [taoensso.timbre :refer [debug info error]]
    [clojure.string :as string]
    [eponai.common.ui.script-loader :as script-loader]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]))

(def video-element-id "sl-video-player")
(def source-element-id "sl-video-player-source")
;(def share-button-id "sl-share-video")

;(defn on-click-event [component event]
;  #?(:cljs
;     (let [button (some #(when (= (.-nodeName %) "BUTTON") %) (array-seq (.-path event)))]
;       (when (some? button)
;         (cond (= (.-id button) share-button-id)
;               (om/update-state! component update :controls/share-video-open? not))))))

(defn controls []
  (string/join
    [
     ;"<div class='plyr__controls'>",
     "<button type='button' data-plyr='play' class='plyr__play-large'>",
     "<svg><use xlink:href='#plyr-play'></use></svg>",
     "<span class='plyr__sr-only'>Play</span>",
     "</button>",



     "<div class='plyr__controls'>",
     "<button type='button' data-plyr='play'>",
     "<svg><use xlink:href='#plyr-play'></use></svg>",
     "<span class='plyr__sr-only'>Play</span>",
     "</button>",

     "<button type='button' data-plyr='pause'>",
     "<svg><use xlink:href='#plyr-pause'></use></svg>",
     "<span class='plyr__sr-only'>Pause</span>",
     "</button>",

     "<span class='plyr__volume'>",
     "<label for='volume{id}' class='plyr__sr-only'>Volume</label>",
     "<input id='volume{id}' class='plyr__volume--input' type='range' min='0' max='10' value='5' data-plyr='volume'>",
     "<progress class='plyr__volume--display' max='10' value='0' role='presentation'></progress>",
     "</span>",

     "<button type='button' data-plyr='mute'>",
     "<svg class='icon--muted'><use xlink:href='#plyr-muted'></use></svg>",
     "<svg><use xlink:href='#plyr-volume'></use></svg>",
     "<span class='plyr__sr-only'>Toggle Mute</span>",
     "</button>",

     "<span class=\"plyr__time\">"
     "<span class=\"plyr__sr-only\">Current time</span>"
     "<span class=\"plyr__time--current\">00:00</span>"
     "</span>"

     "<span class=\"plyr__progress\">"
     "<label for=\"seek{id}\" class=\"plyr__sr-only\">Seek</label>"
     "<input id=\"seek{id}\" class=\"plyr__progress--seek\" type=\"range\" min=\"0\" max=\"100\" step=\"0.1\" data-plyr=\"seek\">"
     "<progress class=\"plyr__progress--played\" max=\"100\" role=\"presentation\"></progress>"
     "<progress class=\"plyr__progress--buffer\" max=\"100\"><span></span>% buffered</progress>"
     "<span class=\"plyr__tooltip\">00:00</span>"
     "</span>"

     "<button type='button' data-plyr='captions'>",
     "<svg class='icon--captions-on'><use xlink:href='#plyr-captions-on'></use></svg>",
     "<svg><use xlink:href='#plyr-captions-off'></use></svg>",
     "<span class='plyr__sr-only'>Toggle Captions</span>",
     "</button>",

     ;"<button type='button' data-plyr='share' id=" share-button-id ">",
     ;"<i class='fa fa-share-alt fa-fw'></i>"
     ;"<span class='plyr__sr-only'>Share Video</span>",
     ;"</button>",

     "<button type='button' data-plyr='fullscreen'>",
     "<svg class='icon--exit-fullscreen'><use xlink:href='#plyr-exit-fullscreen'></use></svg>",
     "<svg><use xlink:href='#plyr-enter-fullscreen'></use></svg>",
     "<span class='plyr__sr-only'>Toggle Fullscreen</span>",
     "</button>",
     "</div>"

     "<div class='plyr__live-indicator'>",
     ;"LIVE",
     "<span>Live</span>",
     "</div>",]
    )
  )

(defn video-is-playing? []
  #?(:cljs
     (let [video-element (utils/element-by-id video-element-id)]
       (and (< 0 (.-currentTime video-element))
            (not (.-paused video-element))
            (not (.-ended video-element))
            (< 2 (.-readyState video-element)))))
  ;var isPlaying = video.currentTime > 0 && !video.paused && !video.ended
  ;&& video.readyState > 2;
  )

(defn hls-supported? []
  #?(:cljs (boolean (.isSupported js/Hls))
     :clj  false))

(defn subscribe-hls [component]
  #?(:cljs
     (let [video-element (utils/element-by-id video-element-id)
           {:keys [source]} (om/props component)
           hls (new js/Hls)]
       (debug "Subscribing hls: " source)
       (when-some [old-hls ^js/Hls (:hlsjs (om/get-state component))]
         (.destroy old-hls))
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
            (fn [event ^js/Hls.EventData data]
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

       (om/update-state! component assoc :hlsjs hls))))

(defn init-video-player [component]
  #?(:cljs
     (let [video-element (utils/element-by-id video-element-id)]
       (when (hls-supported?)
         (subscribe-hls component))

       (.setup js/plyr video-element #js {
                                          :html     (clj->js (controls))

                                          ;:controls #js ["play-large"
                                          ;               "play"
                                          ;               "progress"
                                          ;               "current-time"
                                          ;               "mute"
                                          ;               "volume"
                                          ;               ;"captions"
                                          ;               "fullscreen"]
                                          })
       ;(.addEventListener js/document "click" on-click-fn)
       )))

(defui VideoPlayer-no-loader
  Object
  (componentWillUnmount [this]
    #?(:cljs
       (when-some [hls ^js/Hls (:hlsjs (om/get-state this))]
         (.destroy hls)
         ;(.removeEventListener js/document "click" on-click-fn)
         )))

  (componentDidMount [this]
    #?(:cljs
       (when-not (script-loader/is-loading-scripts? this)
         (init-video-player this))))

  (componentDidUpdate [this prev-props prev-state]
    #?(:cljs
       (let [{:controls/keys [share-video-open?] :keys [hlsjs]} (om/get-state this)]
         (when (and (nil? hlsjs)
                    (not (script-loader/is-loading-scripts? this)))
           ;; hlsjs player was not initialized during didMount because scripts hadn't loaded.
           ;; Do it now.
           (init-video-player this))

         (when-not (= (:controls/share-video-open? prev-state)
                      share-video-open?)
           (let [video-element (utils/element-by-id video-element-id)]
             (if share-video-open?
               (when (video-is-playing?)
                 (.pause video-element))
               (when-not (video-is-playing?)
                 (.play video-element))))))))

  (initLocalState [this]
    {:controls/share-video-open? false})
  (render [this]
    (let [{:keys [source poster]} (om/props this)
          {:controls/keys [share-video-open?]} (om/get-state this)
          {:keys [is-live?]} (om/get-computed this)]
      (dom/div
        (cond->> (css/add-class :video-player-container)
                 share-video-open?
                 (css/add-class :share-open)
                 is-live?
                 (css/add-class :is-live))
        (dom/video
          {:id video-element-id :poster poster}
          (dom/source
            (cond-> {:id source-element-id}
                    (and (not (script-loader/is-loading-scripts? this))
                         (not (hls-supported?)))
                    (assoc :src source))))
        ;(when is-live?
        ;  (dom/div (css/add-class :live-indicator)
        ;           (dom/span nil "LIVE")))

        ;(dom/div
        ;  (css/add-class :sulo-share-video-overlay)
        ;
        ;  (dom/div
        ;    nil
        ;    (dom/p (css/add-class :title) "Share this stream")
        ;    (menu/horizontal
        ;      nil
        ;      (menu/item
        ;        nil
        ;        (social/share-button nil {:platform :social/facebook}))
        ;      (menu/item
        ;        nil
        ;        (social/share-button nil {:platform :social/twitter}))
        ;      (menu/item
        ;        nil
        ;        (social/share-button nil {:platform :social/email})))
        ;
        ;    (dom/div
        ;      nil
        ;      (dom/a (->> {:onClick #(om/update-state! this update :controls/share-video-open? not)}
        ;                  (css/button-hollow)
        ;                  (css/add-class :secondary))
        ;             (dom/span nil "Close")))))
        ))))

(def VideoPlayer (script-loader/js-loader {:component VideoPlayer-no-loader
                                           #?@(:cljs [:scripts [[#(exists? js/Hls)
                                                                 "https://cdn.jsdelivr.net/hls.js/latest/hls.js"]
                                                                [#(exists? js/plyr)
                                                                 "https://cdn.plyr.io/2.0.12/plyr.js"]]])}))

(def ->VideoPlayer (om/factory VideoPlayer))
