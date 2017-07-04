(ns eponai.common.ui.script-loader
  #?(:cljs (:require-macros [eponai.common.macros :refer [ui-with-locals]]))
  (:require
    #?(:cljs [goog.net.jsloader :as jsl])
    #?(:clj [eponai.common.macros :refer [ui-with-locals]])
    [om.next :as om :refer [ui]]
    [om.dom :as dom]
    [taoensso.timbre :refer [debug]]))

;; Inspired by https://www.martinklepsch.org/posts/just-in-time-script-loading-with-react-and-clojuresript.html

(let [sources-loaded (atom #{})]
  (defn filter-loaded [scripts]
    (let [filtered (into [] (comp (remove #(contains? @sources-loaded (val %)))
                                  (remove #((key %)))
                                  (map val))
                         scripts)]
      (swap! sources-loaded into filtered)
      filtered)))

(defprotocol IRenderLoadingScripts
  (render-while-loading-scripts [this props]
    "Return react component/elements to render while scripts are loading."))

(extend-type #?(:clj Object :cljs default)
  IRenderLoadingScripts
  (render-while-loading-scripts [this props]
    (dom/div nil)))

(defn- render-while-loading [component child-props]
  #?(:cljs (render-while-loading-scripts component child-props)
     :clj  (if-let [static-protocol-fn (-> component meta :render-while-loading-scripts)]
             ;; Attempt to call om.next's static protocol
             (static-protocol-fn component child-props)
             (render-while-loading-scripts component child-props))))

(defn is-loading-scripts? [component]
  (not (some-> (om/get-computed component)
               ::js-loader
               (om/get-state)
               ::loaded?)))

(defn- load-scripts [this scripts]
  #?(:cljs
     (let [not-loaded (clj->js (filter-loaded scripts))
           ^js/goog.async.Deferred x (jsl/loadMany not-loaded)]
       (.then x (fn []
                  (debug "Loaded libraries: " not-loaded)
                  (om/update-state! this assoc ::loaded? true))))))

(defn- render-loader [this child-props {:keys [component factory]}]
  (let [{::keys [loaded?]} (om/get-state this)
        child-props (om/computed child-props (assoc (or (om/get-computed this) {})
                                               ::js-loader this
                                               ;; Pass some props to make it update when we've loaded.
                                               ::loaded? loaded?))]
    (if loaded?
      ((or factory (om/factory component)) child-props)
      (render-while-loading component child-props))))

(defn loader-with-query [{:keys [scripts component] :as args}]
  (ui-with-locals
    [component scripts args]
    static om/IQuery
    (query [this] [{:proxy/loaded (om/get-query component)}])
    Object
    (componentDidMount [this] (load-scripts this scripts))
    (render [this]
            (render-loader this (:proxy/loaded (om/props this)) args))))

(defn loader-without-query [{:keys [scripts] :as args}]
  (ui-with-locals
    [scripts args]
    Object
    (componentDidMount [this] (load-scripts this scripts))
    (render [this] (render-loader this (om/props this) args))))

(defn js-loader
  "Takes a map of scripts to load and a component to render when they have loaded.
  The component can staticly implement IRenderLoadingScripts to override the default behavior, i.e:
  (defui Foo
    static IRenderLoadingScripts
    (render-while-loading-scripts [this]
      ...)

  Example arguments
   {:scripts [[#(exists? js/Stripe) \"https://js.stripe.com/v2/\"]]
    :component eponai.common.ui.stripe/Stripe}"
  [{:keys [component] :as args}]
  #?(:cljs (if (om/iquery? component)
             (loader-with-query args)
             (loader-without-query args))
     :clj  component))


(defn stripe-loader
  ([component] (stripe-loader component nil))
  ([component scripts]
    ;; {:pre [(satisfies? IRenderLoadingScripts component)]}
    (letfn [(loaded-stripe? []
              #?(:cljs (exists? js/Stripe)))]
      (js-loader {:component component
                  :scripts   (into [[loaded-stripe? "https://js.stripe.com/v2/"]
                                    [loaded-stripe? "https://js.stripe.com/v3/"]]
                                   scripts)}))))