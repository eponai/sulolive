(ns cljsjs.react
  (:require react
            [goog.object :as gobj]))

(gobj/set js/window "React" react)