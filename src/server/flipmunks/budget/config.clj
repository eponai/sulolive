(ns flipmunks.budget.config)

(def config
  (clojure.edn/read-string (slurp "budget-private/config.edn")))