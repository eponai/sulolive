(ns eponai.common.api.products
  (:require [clojure.string :as s]))

(defn find-by-category [category-path]
  ;; Category rule finds all it's children's items.
  (let [category-rule '[[(category? ?c ?x)
                         [?c :category/path _]
                         [(identity ?c) ?x]]
                        [(category? ?c ?recur)
                         [?c :category/children ?child]
                         (category? ?child ?recur)]]]
    {:where   '[[?category :category/path ?path]
                (category? ?category ?c)
                [?e :store.item/category ?c]]
     :symbols {'?path category-path}
     :rules   category-rule}))

(defn find-all []
  {:where '[[?e :store.item/name]]})