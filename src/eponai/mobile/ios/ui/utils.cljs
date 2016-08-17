(ns eponai.mobile.ios.ui.utils)

(def Dimensions (.-Dimensions js/ReactNative))

(def screen-size {:width (.-width (.get Dimensions "window"))
                  :height (.-height (.get Dimensions "window"))})