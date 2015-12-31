(ns eponai.client.ui
  (:require [clojure.string :as s])
  (:import [java.util UUID]))

(defn ->camelCase [k]
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

(defmacro opts [m]
  `(let [m# ~m
         style# (:style m#)
         key# (:key m#)
         ret# (assoc-if style# m# :style ~(if (and (map? m)
                                                   (contains? m :style)
                                                   (should-inline-style? (:style m)))
                                             (style* (:style m))
                                            `(style* style#)))
         ret2# (assoc-if key# ret# :key (fn [] (unique-str ~(str (UUID/randomUUID)) key#)))]
     (if style#
       (update ret2# :style ~'cljs.core/clj->js)
       ret2#)))

(defmacro style [m & ms]
 `(let [ret# ~(if (should-inline-style? m)
                 (style* m)
                 `(style* ~m))]
    (apply merge {:style (~'cljs.core/clj->js ret#)}
            ~ms)))
