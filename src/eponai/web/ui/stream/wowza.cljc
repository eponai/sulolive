(ns eponai.web.ui.stream.wowza
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.shared :as shared]
    [eponai.common.stream :as stream]
    [eponai.common.ui.script-loader :as script-loader]
    #?(:cljs
       [eponai.web.wowza-player :as wowza-player])
    [taoensso.timbre :refer [debug]]))

(def wowza-element-id "sulo-wowza")

(defn render-stream [this]
  (dom/div {:id wowza-element-id}))

(defui Wowza-no-loader
  static script-loader/IRenderLoadingScripts
  (render-while-loading-scripts [this props]
    (render-stream this))

  Object
  (subscriber-host [this]
    (:source (om/props this)))

  (subscribe-wowza [this]
    (let [{:keys [store allowed-stream-states wowza-player-opts]
           :or   {allowed-stream-states #{:stream.state/live}}} (om/get-computed this)
          {:keys [stream]} (om/props this)]
      #?(:cljs
         (if-let [subscriber-host (.subscriber-host this)]
           (when (contains? (set allowed-stream-states) (:stream/state stream))
             (let [stream-id (stream/stream-id store)
                   stream-url (stream/wowza-live-stream-url subscriber-host stream-id)
                   player (shared/by-key this :shared/wowza-player)]
               (wowza-player/init! player
                                   wowza-element-id
                                   {:license                       "PLAY1-aaEJk-4mGcn-jW3Yr-Fxaab-PAYm4"
                                    :title                         ""
                                    :description                   ""
                                    :sourceURL                     ""
                                    :autoPlay                      true
                                    :volume                        75
                                    :uiShowDurationVsTimeRemaining true
                                    ;"mute"                 false,
                                    ;"loop"                 false,
                                    :audioOnly                     false
                                    ;:posterFrameURL                photo-url
                                    ;:endPosterFrameURL             photo-url
                                    ;:uiPosterFrameFillMode         "fill"
                                    :uiShowQuickRewind             false
                                    :uiShowBitrateSelector         false
                                    ;"uiQuickRewindSeconds" "30"
                                    :wowza-player-opts             wowza-player-opts})
               (wowza-player/play player stream-url)))
           (debug "Hasn't received server-url yet. Needs server-url to start stream.")))))

  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [on-fullscreen-change]} (om/get-state this)]
         (debug "Stream will unmount")
         (wowza-player/destroy (shared/by-key this :shared/wowza-player))
         )))

  (componentDidUpdate [this _ _]
    #?(:cljs
       (when-not (wowza-player/get-player (shared/by-key this :shared/wowza-player))
         (.subscribe-wowza this))))

  (componentDidMount [this]
    (debug "Subscribe wowza")
    (.subscribe-wowza this))

  (render [this]
    (render-stream this)))

(def Wowza (script-loader/js-loader {:component Wowza-no-loader
                                      #?@(:cljs [:scripts [[#(exists? js/WowzaPlayer)
                                                            "//player.wowza.com/player/1.0.10.4565/wowzaplayer.min.js"]]])}))

(def ->Wowza (om/factory Wowza))