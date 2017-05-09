(ns eponai.common.ui.stream
  (:require
    [eponai.common.stream :as stream]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.shared :as shared]
    [taoensso.timbre :refer [debug error info]]
    [eponai.common.ui.script-loader :as script-loader]
    #?(:cljs [eponai.web.wowza-player :as wowza-player])
    #?(:cljs [eponai.web.modules :as modules])))

(defn add-fullscreen-listener [f]
  #?(:cljs
     (doseq [prefix [nil "moz" "webkit"]]
       (.addEventListener js/document (str prefix "fullscreenchange") f))))

(defn remove-fullscreen-listener [f]
  #?(:cljs
     (doseq [prefix [nil "moz" "webkit"]]
       (.removeEventListener js/document (str prefix "fullscreenchange") f))))

(def wowza-element-id "sulo-wowza")

(defn render-stream [this computed]
  (let [{:keys [stream-title widescreen?]} computed]
    (dom/div #js {:id "sulo-video-container" :className (str "flex-video"
                                                             (when widescreen? " widescreen"))}
      (dom/div #js {:className (str "sulo-spinner-container")}
        (dom/i #js {:className "fa fa-spinner fa-spin fa-4x"}))
      (dom/div #js {:id wowza-element-id}))))

(defui Stream-no-loader
  static om/IQuery
  (query [this]
    [{:query/stream [:stream/store :stream/state]}
     {:query/stream-config [:ui.singleton.stream-config/subscriber-url]}])
  static script-loader/IRenderLoadingScripts
  (render-while-loading-scripts [this props]
    (render-stream this (om/get-computed props)))

  Object
  (subscriber-host [this]
    (get-in (om/props this) [:query/stream-config :ui.singleton.stream-config/subscriber-url]))

  (subscribe-wowza [this]
    (let [{:keys [on-fullscreen-change]} (om/get-state this)
          {:keys [store allowed-stream-states wowza-player-opts]
           :or {allowed-stream-states #{:stream.state/live}}} (om/get-computed this)
          {:query/keys [stream]} (om/props this)]
      #?(:cljs
         (if-let [subscriber-host (.subscriber-host this)]
           (when (contains? (set allowed-stream-states) (:stream/state stream))
             (let [stream-id (stream/stream-id store)
                   photo-url (get-in store [:store/profile :store.profile/photo :photo/path] "/assets/img/storefront.jpg")
                   stream-url (stream/wowza-live-stream-url subscriber-host stream-id)
                   _ (debug "photo: url: " photo-url)
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
               (wowza-player/play player stream-url)
               (add-fullscreen-listener on-fullscreen-change)))
           (debug "Hasn't received server-url yet. Needs server-url to start stream.")))))

  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [on-fullscreen-change]} (om/get-state this)]
         (debug "Stream will unmount")
         (wowza-player/destroy (om/shared this :shared/wowza-player))
         (remove-fullscreen-listener on-fullscreen-change))))

  (componentDidUpdate [this _ _]
    #?(:cljs
       (when-not (wowza-player/get-player (shared/by-key this :shared/wowza-player))
         (.subscribe-wowza this))))

  (componentDidMount [this]
    (.subscribe-wowza this))

  (initLocalState [this]
    (let [{:keys [hide-chat? on-fullscreen-change]} (om/get-computed this)]
      {:show-chat?           (not hide-chat?)
       :fullscreen?          false
       :playing?             true
       :on-fullscreen-change #(let [{:keys [fullscreen?]} (om/get-state this)]
                                (om/update-state! this update :fullscreen? not)
                                (when on-fullscreen-change
                                  (on-fullscreen-change (not fullscreen?))))}))

  (render [this]
    (render-stream this (om/get-computed this))))

(def Stream (script-loader/js-loader {:component Stream-no-loader
                                      #?@(:cljs [:scripts [[#(exists? js/WowzaPlayer)
                                                            "//player.wowza.com/player/1.0.10.4565/wowzaplayer.min.js"]]])}))
(def ->Stream (om/factory Stream))

#?(:cljs
   (modules/set-loaded! :stream+chat))