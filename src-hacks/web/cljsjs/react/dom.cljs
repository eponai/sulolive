(ns cljsjs.react.dom
  (:require react-dom
            [goog.object :as gobj]))

(gobj/set js/window "ReactDOM" react-dom)