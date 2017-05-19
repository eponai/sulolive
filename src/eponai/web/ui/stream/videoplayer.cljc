(ns eponai.web.ui.stream.videoplayer
  (:require
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [eponai.web.social :as social]
    [eponai.common.ui.dom :as dom]
    [taoensso.timbre :refer [debug info error]]
    [clojure.string :as string]
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

;(defn controls []
;  (string/join
;    ["<div class='plyr__controls'>",
;     "<button type='button' data-plyr='play'>",
;     "<svg><use xlink:href='#plyr-play'></use></svg>",
;     "<span class='plyr__sr-only'>Play</span>",
;     "</button>",
;
;     "<button type='button' data-plyr='pause'>",
;     "<svg><use xlink:href='#plyr-pause'></use></svg>",
;     "<span class='plyr__sr-only'>Pause</span>",
;     "</button>",
;
;     "<button type='button' data-plyr='mute'>",
;     "<svg class='icon--muted'><use xlink:href='#plyr-muted'></use></svg>",
;     "<svg><use xlink:href='#plyr-volume'></use></svg>",
;     "<span class='plyr__sr-only'>Toggle Mute</span>",
;     "</button>",
;
;     "<span class='plyr__volume'>",
;     "<label for='volume{id}' class='plyr__sr-only'>Volume</label>",
;     "<input id='volume{id}' class='plyr__volume--input' type='range' min='0' max='10' value='5' data-plyr='volume'>",
;     "<progress class='plyr__volume--display' max='10' value='0' role='presentation'></progress>",
;     "</span>",
;
;     "<button type='button' data-plyr='captions'>",
;     "<svg class='icon--captions-on'><use xlink:href='#plyr-captions-on'></use></svg>",
;     "<svg><use xlink:href='#plyr-captions-off'></use></svg>",
;     "<span class='plyr__sr-only'>Toggle Captions</span>",
;     "</button>",
;
;     "<button type='button' data-plyr='share' id=" share-button-id ">",
;     "<i class='fa fa-share-alt fa-fw'></i>"
;     "<span class='plyr__sr-only'>Share Video</span>",
;     "</button>",
;
;     "<button type='button' data-plyr='fullscreen'>",
;     "<svg class='icon--exit-fullscreen'><use xlink:href='#plyr-exit-fullscreen'></use></svg>",
;     "<svg><use xlink:href='#plyr-enter-fullscreen'></use></svg>",
;     "<span class='plyr__sr-only'>Toggle Fullscreen</span>",
;     "</button>",
;     "</div>"]
;    )
;  )

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
         (.destroy hlsjs)
         ;(.removeEventListener js/document "click" on-click-fn)
         )))

  (componentDidMount [this]
    #?(:cljs
       (let [video-element (utils/element-by-id video-element-id)]
         (when (.hls-supported? this)
           (.subscribe-hls this))

         (.setup js/plyr video-element #js {:controls #js ["play-large"
                                                           "play"
                                                           ;"progress"
                                                           ;"current-time"
                                                           "mute"
                                                           "volume"
                                                           ;"captions"
                                                           "fullscreen"]})
         ;(.addEventListener js/document "click" on-click-fn)
         )))

  (componentDidUpdate [this prev-props prev-state]
    #?(:cljs
       (let [{:controls/keys [share-video-open?]} (om/get-state this)]
         (when-not (= (:controls/share-video-open? prev-state)
                      share-video-open?)
           (let [video-element (utils/element-by-id "sl-video-player")]
             (if share-video-open?
               (when (video-is-playing?)
                 (.pause video-element))
               (when-not (video-is-playing?)
                 (.play video-element))))))))

  (initLocalState [this]
    {:controls/share-video-open? false})
  (render [this]
    (let [{:keys [source]} (om/props this)
          {:controls/keys [share-video-open?]} (om/get-state this)]
      (dom/div
        (cond->> (css/add-class :video-player-container)
                 share-video-open?
                 (css/add-class :share-open))
        (dom/video
          {:id video-element-id}
          (dom/source
            (cond-> {:id source-element-id}
                    (not (.hls-supported? this))
                    (assoc :src source))))
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

(def ->VideoPlayer (om/factory VideoPlayer))