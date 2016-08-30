(ns eponai.mobile.components.button
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [touchable-highlight touchable-opacity text view]]))

(defn button [{:keys [title on-press key params]} style text-style]
  (touchable-highlight
    (opts (merge (update {:onPress on-press
                          :key     key
                          :style   {:border-radius 3 :padding 10 :height 44 :justify-content "center"}}
                         :style merge (merge style (:style params)))
                 (dissoc params :style)))
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

(defn custom [{:keys [on-press key]} content-view]
  (touchable-highlight
    (opts {:onPress on-press
           :key key
           :style {:border-radius 0 :padding 5 :justify-content "space-between"}})
    content-view))

(defn primary-hollow [params]
  (button (merge {:params {:underlayColor "#044e8a"}}
                 params)
          {:background-color "white"
           :border-color "#044e8a"
           :border-width 1}
          {:color "#044e8a"}))

(defn inline-selection [params]
  (button params
          {:background-color "transparent"
           :border-color "#999"
           :border-width 1
           :padding 5
           :height 34}
          {:color "#999"}))

(defn facebook [params]
  (button params
          {:background-color "#4267B2"}
          {:color "white"}))

(defn twitter [params]
  (button params
          {:background-color "#4099FF"}
          {:color "white"}))

(defn google+ [params]
  (button params
          {:background-color "#d34836"}
          {:color "white"}))