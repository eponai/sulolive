(ns eponai.mobile.components.button
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [touchable-highlight touchable-opacity text]]))

(defn button [{:keys [title on-press]} style text-style]
  (touchable-highlight
    (opts (update {:onPress on-press
                   :style {:border-radius 5 :padding 10 :height 44 :justify-content "center"}}
                  :style merge style))
    (text
      (opts (update {:style {:text-align "center" :font-weight "bold"}}
                    :style merge text-style))
      (or title ""))))

(defn primary [params]
  (button params
          {:background-color "#044e8a"}
          {:color "white"}))

(defn secondary [params]
  (button params
          {:background-color "#999"}
          {:color "white"}))

(defn list-item [params]
  (button params
          {:background-color "#fff"
           :border-radius 0}
          {:color "black"
           :font-weight "normal"}))