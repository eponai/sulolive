(ns eponai.client.local-storage
  #?(:cljs (:require [cognitect.transit :as transit])))

(defprotocol ILocalStorage
  (set-item! [this name item])
  (get-item [this name])
  (remove-item! [this name]))

(defn ->local-storage
  "Local storage wrapper which can read and write clojure values.
  Using transit on cljs side and using an atom in clj (fullstack tests)."
  []
  #?(:cljs (let [reader (transit/reader :json)
                 writer (transit/writer :json)]
             (reify ILocalStorage
               (set-item! [this name item]
                 (let [transit-item (transit/write writer item)]
                   (.setItem js/localStorage name transit-item)))
               (get-item [this name]
                 (transit/read reader (.getItem js/localStorage name)))
               (remove-item! [this name]
                 (.removeItem js/localStorage name))))

     :clj  (let [store (atom {})]
             (reify ILocalStorage
               (set-item! [this name item]
                 (swap! store assoc name item))
               (get-item [this name]
                 (get @store name))
               (remove-item! [this name]
                 (swap! store dissoc name))))))
