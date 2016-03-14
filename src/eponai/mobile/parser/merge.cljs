(ns eponai.mobile.parser.merge)

(defmulti mobile-merge (fn [_ k _] k))
(defmethod mobile-merge :default
  [_ _ _]
  nil)
