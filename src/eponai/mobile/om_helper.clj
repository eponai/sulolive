(ns eponai.mobile.om-helper
  (:require [om.next :as om]))

;; Inspired by
;; https://github.com/hugoduncan/navigator-repro/blob/master/src/navigator_repro/macros.clj

(defmacro with-om-vars
  "Returns the body of (render [this] ... ) which contains bindings
  for various om.next dynamic vars, which is needed when calling
  functions returned by (om/factory Class)."
  [component & body]
  (let [render-with-bindings (get-in om/reshape-map [:reshape 'render])
        [_ [this-sym] render-body] (render-with-bindings
                                     `(~'render [~'this]
                                        ~@body))]
    `(let [~this-sym ~component]
       ~render-body)))
