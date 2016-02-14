(ns eponai.client.testdata
  (:require [datomic.api :as d]
            [eponai.server.datomic_dev :as datomic_dev]
            [eponai.server.datomic.pull :as pull]
            [clojure.java.io :as io]
            [clojure.tools.reader.edn :as edn]))

(defmacro inline-datomic-schema
  "Compiles our schema-0.edn into a clojurescript file (testdata.cljs)
  so we can use it in our tests."
  []
  (let [schema# (datomic_dev/read-schema-files)
        conn# (datomic_dev/create-new-inmemory-db "read-datomic-schema")
        _ (d/transact conn# schema#)
        inlined# (into [] (pull/schema (d/db conn#)))]
    inlined#))
