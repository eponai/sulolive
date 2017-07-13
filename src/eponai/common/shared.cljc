(ns eponai.common.shared
  (:require [om.next :as om]
            [taoensso.timbre :refer [debug]]))

(defprotocol IStoppableComponent
  (stop [this] "Called when clearing components in development."))

(extend-type #?(:clj Object :cljs default)
  IStoppableComponent
  (stop [this]))

;; Returns a shared-component based on its shared key and which environment to use.
(defmulti shared-component (fn [reconciler key env] [key env]))
(defmethod shared-component :default
  [key env]
  (throw (ex-info (str "No shared component registered for key: " key " environment: " env
                       ". Make sure the component is (:required ..) by some namespace.")
                  {:key key :env env})))

;; All shared components are created only once.
(let [components (atom {})]
  (defn singleton-components [reconciler key env]
    (if-let [component (get @components [key env])]
      component
      (let [component (shared-component reconciler key env)]
        #?(:cljs
           (swap! components assoc [key env] component))
        component)))

  (defn clear-components! []
    (run! (fn [component]
            (stop component))
          (vals @components))
    (reset! components {})))

(defn by-key [x key]
  {:pre [(or (om/reconciler? x) (om/component? x))]}
  (let [shared (if (om/component? x)
                 (om/shared x key)
                 (get-in x [:config :shared key]))
        reconciler (cond-> x (om/component? x) (om/get-reconciler))]
    (cond->> shared
             ;; If it's a keyword, it describes which environment it's used in. Get it from multimethod.
             (keyword? shared)
             (singleton-components reconciler key))))