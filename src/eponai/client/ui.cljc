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

(defn map-all
  "same map but with switched parameter order"
  [coll f]
  (map f coll))

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

(defn ->camelCase [k]
  {:pre [(keyword? k)]}
  (when (namespace k)
    (throw (str "cannot camelCase a keyword with a namespace. key=" k)))
  (let [[a & xs] (s/split (name k) #"-")]
    (s/join (cons a (map s/capitalize xs)))))

;; Using memoize, since the number of possible keys is limited to css keys
;; TODO: Make this a macro, so that the transformations are made in compile time
(let [camel-case (memoize ->camelCase)]
  (defn style* [style-map]
     (->> style-map
          (reduce-kv (fn [acc k v]
                       (assoc! acc (camel-case k) v))
                     (transient {}))
          persistent!)))

(defn should-inline-style? [m]
  (and (map? m)
       (every? keyword? (keys m))))

(defn unique-str [uuid v]
  (if (vector? v)
    (s/join "-" (concat ["uniq" uuid] v))
    (do
      (warn "The value of :key was not a vector. Value: " v)
      v)))

(defmacro opts
  "Takes a map and makes it easier to use its :key and :style keys.
  This is macro is used for sablono element's second argument maps.

  What it does for the options:
  :key, React needs html elements to be unique for some reason.
        And you'd normally pass a unique string to the :key attribute.
        With this macro, you can just pass a vector of unique values
        and we'll generate a unique string.
  :style, The keys to the :style map should be camelCased to work with
          react. We want to work with kebab-cased keywords, because
          that's what we use everywhere else."
  [m]
  (let [inline-style (and (map? m)
                          (contains? m :style)
                          (should-inline-style? (:style m))
                          (style* (:style m)))]
    `(let [m# ~m
           inline-style# ~inline-style]
       (cond-> m#
               (:key m#) (assoc :key (unique-str ~(str #?(:clj (UUID/randomUUID)
                                                          :cljs (random-uuid))) (:key m#)))
               (:style m#) (assoc :style (cljs.core/clj->js (if inline-style#
                                                              inline-style#
                                                              (style* (:style m#)))))))))

(defmacro style [m & ms]
  (let [inline-style (and (should-inline-style? m)
                          (style* m))]
    `(let [inline-style# ~inline-style
           ret# (if inline-style#
                  inline-style#
                  (style* ~m))]
      (apply merge {:style (cljs.core/clj->js ret#)}
             ~ms))))

;; Deep camelCase map macro -------------

(let [camel-case (memoize ->camelCase)]
  (defn camel*
    ([x] (camel* x identity))
    ([x symbol-fn]
     {:pre [(fn? symbol-fn)]}
     (if-not (map? x)
       x
       (persistent!
         (reduce-kv (fn [m k v]
                      (assoc! m (keyword (camel-case k))
                              (cond
                                (map? v) (camel* v symbol-fn)
                                (symbol? v) (symbol-fn v)
                                :else v)))
                    (transient {})
                    x))))))

(defmacro camel
  "camelCases every key in the map recursively and in child maps."
  [m]
  (let [inline-style (when (map? m)
                       (camel* m (fn [v] `(camel* ~v))))]
    `(let [inline# ~inline-style]
       (if inline#
         inline#
         (camel* ~m)))))
