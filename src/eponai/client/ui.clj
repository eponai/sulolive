(ns eponai.client.ui
  (:require [clojure.walk :as w]
            [clojure.string :as s])
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

(defn unique-value [v]
  (if (vector? v)
    (s/join "--" (concat ["unique-key" (UUID/randomUUID)]
                         v))
    v))

(defmacro assoc-if [bool m k v]
  `(if ~bool
     (assoc ~m ~k ~v)
     ~m))

(defmacro opts [{:keys [style key] :as m}]
  (let [m# (assoc-if style m :style (style* style))
        m2# (assoc-if key m# :key (unique-value key))]
    (if style
      `(update ~m2# :style ~'cljs.core/clj->js)
      `~m2#)))

(defmacro style [m & ms]
  (let [ret# (style* m)]
    `(apply merge {:style (~'cljs.core/clj->js ~ret#)}
            ~ms)))

