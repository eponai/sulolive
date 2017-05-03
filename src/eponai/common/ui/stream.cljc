(ns eponai.common.ui.stream
  (:require
    [eponai.common.stream :as stream]
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs [eponai.web.utils :as utils])
    [taoensso.timbre :refer [debug error info]]))

(defn add-fullscreen-listener [f]
  #?(:cljs
     (doseq [prefix [nil "moz" "webkit"]]
       (.addEventListener js/document (str prefix "fullscreenchange") f))))

(defn remove-fullscreen-listener [f]
  #?(:cljs
     (doseq [prefix [nil "moz" "webkit"]]
       (.removeEventListener js/document (str prefix "fullscreenchange") f))))

(def wowza-element-id "sulo-wowza")

(defui Stream
  static om/IQuery
  (query [this]
    [{:query/stream-config [:ui.singleton.stream-config/subscriber-url]}])
  Object
  (subscriber-host [this]
    (get-in (om/props this) [:query/stream-config :ui.singleton.stream-config/subscriber-url]))

  (subscribe-wowza [this]
    (let [{:keys [on-fullscreen-change]} (om/get-state this)]
      #?(:cljs
         (if-let [subscriber-host (.subscriber-host this)]
           (let [store (:store (om/get-computed this))
                 stream-id (stream/stream-id store)
                 photo-url (get-in store [:store/profile :store.profile/photo :photo/path] "/assets/img/storefront.jpg")
                 stream-url (stream/wowza-live-stream-url subscriber-host stream-id)
                 _ (debug "photo: url: " photo-url)
                 player (js/WowzaPlayer.create
                          wowza-element-id
                          #js {:license                       "PLAY1-aaEJk-4mGcn-jW3Yr-Fxaab-PAYm4"
                               :title                         ""
                               :description                   "",
                               :sourceURL                     stream-url
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
                               })]
             (add-fullscreen-listener on-fullscreen-change))
           (debug "Hasn't received server-url yet. Needs server-url to start stream.")))))

  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [on-fullscreen-change]} (om/get-state this)
             player (js/WowzaPlayer.get wowza-element-id)]
         (debug "Stream will unmount")
         (when player
           (.destroy player))
         (remove-fullscreen-listener on-fullscreen-change))))

  (componentDidUpdate [this _ _]
    #?(:cljs
       (when-not (js/WowzaPlayer.get wowza-element-id)
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
    (let [{:keys [stream-title widescreen?]} (om/get-computed this)]
      (dom/div #js {:id "sulo-video-container" :className (str "flex-video"
                                                               (when widescreen? " widescreen"))}
        (dom/div #js {:className (str "sulo-spinner-container")}
          (dom/i #js {:className "fa fa-spinner fa-spin fa-4x"}))
        (dom/div #js {:id wowza-element-id})))))

(def ->Stream (om/factory Stream))
