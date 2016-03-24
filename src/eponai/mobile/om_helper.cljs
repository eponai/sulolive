(ns eponai.mobile.om-helper
  (:require [om.next :as om]
            [taoensso.timbre :refer-macros [debug warn]]))

(defn react-ref
  "Wrapper around react-ref to always turn keywords into strings."
  [component ref]
  {:pre [(or (keyword? ref) (string? ref))]}
  (some-> component
    (om/react-ref (cond-> ref (keyword? ref) str))))

(defn get-ref-in
  "Gets react ref in components. Like get-in, but with a component
  instead of a map and refs instead of keys. Returns nil if no ref
  was found.

  Keywords are casted to strings with str."
  [component refs]
  {:pre [(or (nil? refs) (sequential? refs))]}
  (if-let [ref (and component (first refs))]
    (recur (react-ref component ref) (next refs))
    component))

(defn subquery-in
  "Like om/subquery, but can traverse refs like (get-in m [k1 ... kn]).
  Defaults to om/get-query of subquery-class if 'x' component is mounted."
  [x refs subquery-class]
  {:pre [(every? #(or (keyword? %) (string? %)) refs)
         (fn? subquery-class)]}
  (if-let [ref-component (and (om/component? x)
                              (om/mounted? x)
                              (get-ref-in x refs))]
    (om/get-query ref-component)
    (and (om/iquery? subquery-class)
         (om/get-query subquery-class))))

(defn set-runtime-query!
  "Sets components query to the value of calling om/query.

  Useful when query changes at runtime, for example when
  using om/subquery, which relies on refs to set the query.
  The refs are set when render happens and we call this method
  in componentDidUpdate (which is called after render)."
  [c]
  {:pre [(om/component? c)]}
  (let [query (om/query c)]
    (debug "Setting query: " query)
    (om/set-query! c {:query query})))
