(ns eponai.web.wowza-player
  (:require [taoensso.timbre :refer [error]]))

(defprotocol IWowzaPlayer
  (init! [this element-id config] "Initializes a player. Will use the element-id and config for the rest of the calls")
  (play [this source-url] "Starts playing a video using the source-url")
  (get-player [this] "Gets the wowza player instance")
  (destroy [this] "Destroys the player (hate the game)"))

(defn real-player []
  (let [state (atom {})]
    (reify IWowzaPlayer
      (init! [this element-id config]
        (swap! state assoc ::element-id element-id ::config config)
        (js/WowzaPlayer.create element-id (clj->js config)))
      (play [this source-url]
        (let [{::keys [element-id config]} @state]
          (js/WowzaPlayer.create element-id (clj->js (assoc config :sourceURL source-url)))))
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
  (let [player (real-player)]
    (reify IWowzaPlayer
      (init! [_ id config]
        (init! player id config))
      (play [_ _]
        nil)
      (get-player [_]
        (get-player player))
      (destroy [_]
        (destroy player)))))
