(ns eponai.mobile.components.nav
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.client.ui.color :as color]
    [eponai.mobile.components :refer [navigator-ios]]))

(defn navigator [{:keys [initial-route style ref navigation-bar-hidden]}]
  (navigator-ios
    (opts
      (cond-> {:initialRoute        initial-route
               :style               (merge {:flex 1} style)
               :itemWrapperStyle    {:flex            1
                                     :padding         8
                                     :paddingTop      (if navigation-bar-hidden 20 64)
                                     :backgroundColor color/white}
               :backgroundColor     color/white
               :navigationBarHidden navigation-bar-hidden
               :barTintColor        "white"
               :shadowHidden        true}

              (some? ref)
              (assoc :ref ref)))))

;(defn modal-navigator
;  [])

;(defn clean-navigator [{:keys [initial-route]}]
;  (navigator-ios
;    (opts
;      {:initialRoute       initial-route
;       :style               {:flex 1}
;       :itemWrapperStyle    {:flex      1
;                             ;:margin    10
;                             :marginTop 20}
;       :navigationBarHidden true
;       :interactivePopGestureEnabled true})))