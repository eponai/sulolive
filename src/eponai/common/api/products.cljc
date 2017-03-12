(ns eponai.common.api.products
  (:require [clojure.string :as s]))

(defn find-by-category [c]
  {:where   '[[?c :collection/id ?coll]
              [?e :store.item/collection ?c]]
   :symbols {'?coll (.toLowerCase c)}})

(defn find-all []
  {:where '[[?e :store.item/name]]})