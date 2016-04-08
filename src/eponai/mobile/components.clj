(ns eponai.mobile.components
  (:require [natal-shell.components-list :as cl]
            [natal-shell.utils :refer [to-kebab]]
            [om.util]))

(defn components []
  (let [not-yet-included ["Picker"
                          "Picker.Item"
                          "Navigator.NavigationBar"]]
    (into (set cl/components) not-yet-included)))

(defn wrap-component-fn [js-name]
  (let [react-sym (symbol (str "js/React." js-name))]
    `(let [factory# (js/React.createFactory ~react-sym)]
       (defn ~(symbol (to-kebab js-name)) [props# & children#]
         ;; Call str on ref to keep colon in keywords. :foo -> ":foo"
         ;; See (om/factory)
         (let [ref# (:ref props#)]
           (.apply
             factory# nil
             (cljs.core/into-array
               (cons
                 (cljs.core/clj->js (cljs.core/cond-> props#
                                                      (cljs.core/keyword? ref#)
                                                      (cljs.core/update :ref cljs.core/str)))
                 (om.util/force-children children#)))))))))

(defmacro create-component-functions
  "Wraps components which natal hasn't wrapped yet."
  []
  `(do ~@(map wrap-component-fn (components))))
