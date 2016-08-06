(ns eponai.mobile.components.fade-in-view
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :as c]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [debug]]))

(defui FadeInView
  Object
  (initLocalState [_]
    {:fade-animation (js/ReactNative.Animated.Value. 0)})
  (componentDidMount [this]
    (let [{:keys [fade-animation]} (om/get-state this)]
      (debug  "Component did mount")
      (.. js/ReactNative.Animated
          (timing fade-animation #js {:toValue 1
                                      :duration 200})
          start)))

  (render [this]
    (let [{:keys [fade-animation]} (om/get-state this)
          {:keys [children]} (om/props this)]
      (debug "Fade-animation: " fade-animation)
      (c/create-element js/ReactNative.Animated.View
                        (opts {:style {:opacity fade-animation}})
                        children))))

(def ->FadeInView (om/factory FadeInView))