(ns eponai.common.macros
  (:require
    [om.next :as om]
    [clojure.walk :as walk]))

(defmacro with-defs [vars & body]
  {:pre [(every? symbol? vars)]}
  (let [syms (into {} (map (juxt identity #(gensym (str (name %) "_")))) vars)
        defs (map (fn [[sym gen]]
                    `(def ~gen ~sym))
                  syms)]
    `(do
       ~@defs
       ~@(walk/postwalk-replace syms body))))

(defmacro ui-with-locals [vars & ui-body]
  (if (boolean (:ns &env))
    `(om/ui ~@ui-body)
    `(with-defs ~vars (om/ui ~@ui-body))))

(comment
  (let [foo 1
        component (ui-with-locals [foo]
                    Object
                    (render [this] foo))]
    (.render ((om/factory component) {}))) ;; => 1
  )
