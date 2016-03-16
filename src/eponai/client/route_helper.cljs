(ns eponai.client.route-helper
  (:require [clojure.string :as s]
            [bidi.bidi :as bidi]))

(defn- create-route [root paths]
  (letfn [(trim-separators [s]
            (let [s (str s)]
              (cond-> s
                      (s/starts-with? s "/") (->> rest (apply str))
                      (s/ends-with? s "/") (->> butlast (apply str)))))]
    (s/join "/" (cons root (map trim-separators paths)))))

(defn create-outside-route-fn
  "Takes any number of paths and creates a path outside our app."
  []
  (fn [& paths] (create-route "" paths)))

(defn create-inside-route-fn
  "Takes any number of paths and creates a path inside our app."
  [root]
  (fn [& paths] (create-route root paths)))


(defprotocol RouteParamHandler
  (handle-route-params [this params reconciler]))

(defrecord UiComponentMatch [component factory route-param-fn]
  RouteParamHandler
  (handle-route-params [_ reconciler params]
    (route-param-fn reconciler params))
  bidi/Matched
  (resolve-handler [this m]
    (bidi/succeed this m))
  (unresolve-handler [this m] (when (= this (:handler m)) "")))
