(ns eponai.mobile.components.nav
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [navigator-ios]]))

(defn navigator [{:keys [initial-route style ref navigation-bar-hidden]}]
  (navigator-ios
    (opts
      (cond-> {:initialRoute        initial-route
               :style               (merge {:flex 1} style)
               :itemWrapperStyle    {:flex      1
                                     :margin    10
                                     :marginTop (if navigation-bar-hidden 20 64)}
               :navigationBarHidden navigation-bar-hidden
               :barTintColor        "white"
               :shadowHidden        true}

              (some? ref)
              (assoc :ref ref)))))
