(ns eponai.client.ui
  (:require [clojure.string :as s]
            #?(:cljs [om.next :as om])
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [warn]])
  #?(:clj (:import [java.util UUID])))

#?(:cljs
   (defn update-query-params!
     "Updates query params by applying f to the current params of the component with & args.
     Also schedules a re-render of the component."
     ([component f & args]
      (om/set-query! component
                     {:params (apply f (om/get-params component) args)}
                     []))))

#?(:clj
   (defmacro component-implements
     "Returns an object that implements the protocol or nil."
     [protocol x]
     `(if (cljs.core/implements? ~protocol ~x)
        ~x
        ;; in advanced, statics will get elided
        (when (goog/isFunction ~x)
          (let [y# (js/Object.create (. ~x -prototype))]
            (when (cljs.core/implements? ~protocol y#)
              y#))))))
