(ns eponai.common.api.products
  (:require [clojure.string :as s]))

(defn find-by-category [c]
  {:where   '[[?c :category/path ?coll]
              [?e :store.item/categories ?c]]
   :symbols {'?coll c}})

(defn find-all []
  {:where '[[?e :store.item/name]]})