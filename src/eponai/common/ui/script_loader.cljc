(ns eponai.common.ui.script-loader
  #?(:cljs (:require-macros [eponai.common.macros :refer [ui-with-locals]]))
  (:require
    #?(:cljs [goog.net.jsloader :as jsl])
    #?(:clj [eponai.common.macros :refer [ui-with-locals]])
    [om.next :as om :refer [ui]]
    [taoensso.timbre :refer [debug]]))

;; Inspired by https://www.martinklepsch.org/posts/just-in-time-script-loading-with-react-and-clojuresript.html

(defn filter-loaded [scripts]
  (reduce (fn [acc [loaded? src]]
            (if (loaded?) acc (conj acc src)))
          []
          scripts))

(defprotocol IRenderLoadingScripts
  (render-while-loading-scripts [this props]
    "Return react component/elements to render while scripts are loading."))

(extend-type #?(:clj Object :cljs default)
  IRenderLoadingScripts
  (render-while-loading-scripts [this props]
    ((om/factory this) props)))

(defn- render-while-loading [component child-props]
  #?(:cljs (render-while-loading-scripts component child-props)
     :clj  (if-let [static-protocol-fn (-> component meta :render-while-loading-scripts)]
             ;; Attempt to call om.next's static protocol
             (static-protocol-fn component child-props)
             (render-while-loading-scripts component child-props))))

(defn is-loading-scripts? [component]
  (true? (some-> (om/get-computed component)
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
  (let [computed (om/get-computed this)
        child-props (cond-> child-props
                            ;; Add the computed to the child props.
                            (seq computed)
                            (om/computed (assoc computed ::js-loader this)))]
    (if (::loaded? (om/get-state this))
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
  (if (om/iquery? component)
    (loader-with-query args)
    (loader-without-query args)))


(defn stripe-loader [component]
  ;; {:pre [(satisfies? IRenderLoadingScripts component)]}
  #?(:cljs
     (letfn [(loaded-stripe? [] (exists? js/Stripe))]
       (js-loader {:scripts   [[loaded-stripe? "https://js.stripe.com/v2/"]
                               [loaded-stripe? "https://js.stripe.com/v3/"]]
                   :component component}))
     :clj
     (let [loader (js-loader {:component component})]
       loader)))
