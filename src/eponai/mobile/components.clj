(ns eponai.mobile.components
  (:require [natal-shell.components-list :as cl]
            [natal-shell.components :as c]
            [natal-shell.utils :refer [to-kebab]]
            [om.next]
            [om.util]))

(defn components []
  (let [not-yet-included ["Picker"
                          "Picker.Item"
                          "Navigator.NavigationBar"
                          "NavigationExperimental.CardStack"
                          "NavigationExperimental.Header"
                          "NavigationExperimental.Header.Title"]]
    (into (set cl/components) not-yet-included)))

(defn wrap-component-fn [js-name]
  (let [react-sym (symbol (str "js/ReactNative." js-name))]
    `(let [factory# (js/React.createFactory ~react-sym)]
       (defn ~(symbol (to-kebab js-name)) [props# & children#]
         ;; Call str on ref to keep colon in keywords. :foo -> ":foo"
         ;; See (om/factory)
         (let [ref# (:ref props#)
               cljs-props-keys# [:cljs-props :cljsProps]
               cljs-props# (some #(get props# %) cljs-props-keys#)
               props# (cljs.core/clj->js (cljs.core/cond-> (cljs.core/apply cljs.core/dissoc props# cljs-props-keys#)
                                                           (cljs.core/keyword? ref#)
                                                           (cljs.core/update :ref cljs.core/str)))]
           ;; Avoid calling clj->js on the :om-props key.
           (cond-> props#
                   (cljs.core/some? cljs-props#)
                   (goog.object/set "eponai$props" cljs-props#))
           (.apply
             factory# nil
             (cljs.core/into-array (cons props# (om.util/force-children children#)))))))))

(defmacro create-component-functions
  "Wraps components which natal hasn't wrapped yet."
  []
  `(do ~@(map wrap-component-fn (components))))
