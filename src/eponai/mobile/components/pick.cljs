(ns eponai.mobile.components.pick
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :as c :refer [picker-ios]]
    [eponai.mobile.components.button :as button]))

(def PickerItemIOS js/ReactNative.PickerIOS.Item)
(def AnimatedView js/ReactNative.Animated.View)


(defn expandable [{:keys [title on-press data is-expanded? animation]} & [picker-options]]

  (c/create-element
    AnimatedView
    nil
    (if is-expanded?

      (picker-ios
        (when picker-options
          (opts picker-options))

        (map (fn [val]
               (c/create-element
                 PickerItemIOS
                 {:key   val
                  :value val
                  :label (name val)}))
             data))

      (button/list-item {:title    title
                         :on-press #(do
                                     (.start (.timing js/ReactNative.Animated animation #js {:toValue 1 :duration 2000}))
                                     (when on-press
                                       (on-press)))}))))