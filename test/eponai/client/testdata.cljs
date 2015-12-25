(ns eponai.client.testdata
  (:require-macros [eponai.client.testdata :refer [inline-datomic-schema]])
  (:require [eponai.client.datascript :as eponai.datascript]))

(def datomic-schema (inline-datomic-schema))

(defn datascript-schema [] (eponai.datascript/schema-datomic->datascript datomic-schema))
