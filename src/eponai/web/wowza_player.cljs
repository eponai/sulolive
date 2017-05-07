(ns eponai.web.wowza-player
  (:require
    [goog.object :as gobj]
    [taoensso.timbre :refer [error debug]]))

(defprotocol IWowzaPlayer
  (init! [this element-id config] "Initializes a player. Will use the element-id and config for the rest of the calls")
  (play [this source-url] "Starts playing a video using the source-url")
  (get-player [this] "Gets the wowza player instance")
  (destroy [this] "Destroys the player (hate the game)"))

(defn real-player []
  (let [state (atom {})]
    (reify IWowzaPlayer
      (init! [this element-id config]
        (swap! state assoc ::element-id element-id ::config config))
      (play [this source-url]
        (let [{::keys [element-id config]} @state
              {:keys [on-started-playing on-error-retry-forever?]} (:wowza-player-opts config)
              player (js/WowzaPlayer.create element-id (-> config
                                                           (assoc :sourceURL source-url)
                                                           (dissoc :wowza-player-opts)
                                                           (clj->js)))]
          (.onStats player (fn stats-listener [stats]
                             (debug "STATS: " stats)
                             (when on-started-playing
                               (debug "Calling on-started-playing")
                               (on-started-playing player))
                             (debug "Removing stats listener")
                             (.removeOnStats player stats-listener)))
          (.onError player (fn [error]
                             (let [error-id (gobj/get error "eventID")]
                               (debug "Error id: " error-id)
                               (when (and on-error-retry-forever?
                                          (= error-id "onAssetPlayerError"))
                                 (debug "Retrying while the error is onAssetPlayerError")
                                 (play this source-url)))))
          player
          ))
      (get-player [this]
        (js/WowzaPlayer.get (::element-id @state)))
      (destroy [this]
        (when-let [player (get-player this)]
          (try
            (.destroy player)
            (catch :default e
              (error "Error destroying wowza: " e))))))))

(defn fake-player
  "Calls the real player on everything except play, which is a no-op."
  []
  (reify IWowzaPlayer
    (init! [_ id config]
      nil)
    (play [_ _]
      nil)
    (get-player [_]
      nil)
    (destroy [_]
      nil)))
