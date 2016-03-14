(ns eponai.web.parser.merge)

(defmulti web-merge (fn [_ k _] k))
(defmethod web-merge :default
  [_ _ _]
  nil)
