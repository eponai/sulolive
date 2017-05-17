(ns eponai.common.ui.stream
  (:require
    [eponai.common.stream :as stream]
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.shared :as shared]
    [taoensso.timbre :refer [debug error info]]
    [eponai.common.ui.script-loader :as script-loader]
    [eponai.web.ui.stream.videoplayer :as video]
    #?(:cljs [eponai.web.modules :as modules])
    [eponai.web.ui.stream.wowza :as w]
    [eponai.common.ui.elements.css :as css]))

(defn add-fullscreen-listener [f]
  #?(:cljs
     (doseq [prefix [nil "moz" "webkit"]]
       (.addEventListener js/document (str prefix "fullscreenchange") f))))

(defn remove-fullscreen-listener [f]
  #?(:cljs
     (doseq [prefix [nil "moz" "webkit"]]
       (.removeEventListener js/document (str prefix "fullscreenchange") f))))

(defui Stream
  static om/IQuery
  (query [this]
    [{:query/stream [:stream/store :stream/state]}
     {:query/stream-config [:ui.singleton.stream-config/subscriber-url]}])

  Object
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [on-fullscreen-change]} (om/get-state this)]
         (debug "Stream will unmount")
         (remove-fullscreen-listener on-fullscreen-change))))
  (componentDidMount [this]
    #?(:cljs
      (let [{:keys [on-fullscreen-change]} (om/get-state this)]
        (add-fullscreen-listener on-fullscreen-change))))

  (initLocalState [this]
    (let [{:keys [on-fullscreen-change]} (om/get-computed this)]
      {:fullscreen?          false
       :playing?             true
       :on-fullscreen-change #(let [{:keys [fullscreen?]} (om/get-state this)]
                               (om/update-state! this update :fullscreen? not)
                               (when on-fullscreen-change
                                 (on-fullscreen-change (not fullscreen?))))}))

  (render [this]
    (let [{:query/keys [stream stream-config]} (om/props this)
          {:keys [widescreen? store]} (om/get-computed this)
          subscriber-url (:ui.singleton.stream-config/subscriber-url stream-config)
          subscriber-url "http://192.168.1.12:1935"
          stream-id (stream/stream-id store)
          stream-url (stream/wowza-live-stream-url subscriber-url stream-id)
          {:stream/keys [title]} stream]
      (dom/div
        {:id "sulo-video-container" :classes [(str "flex-video"
                                                   (when widescreen? " widescreen"))]}
        (dom/div {:classes ["sulo-spinner-container"]}
                 (dom/span {:classes ["sl-loading-signal"]}))
        (dom/div
          (css/add-class :video-player-container)
          (video/->VideoPlayer {:source stream-url}))))))

;(def Stream (script-loader/js-loader {:component Stream-no-loader
;                                      #?@(:cljs [:scripts [[#(exists? js/WowzaPlayer)
;                                                            "//player.wowza.com/player/1.0.10.4565/wowzaplayer.min.js"]]])}))
(def ->Stream (om/factory Stream))

#?(:cljs
   (modules/set-loaded! :stream+chat))