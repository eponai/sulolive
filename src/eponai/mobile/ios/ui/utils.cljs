(ns eponai.mobile.ios.ui.utils)

(def Dimensions (.-Dimensions js/ReactNative))

(def screen-size {:width (.-width (.get Dimensions "window"))
                  :height (.-height (.get Dimensions "window"))})

(defn position
  [pred coll]
  (first (keep-indexed (fn [idx x]
                         (when (pred x)
                           idx))
                       coll)))