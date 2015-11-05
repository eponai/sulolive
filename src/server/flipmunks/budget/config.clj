(ns flipmunks.budget.config)

(def config
  (read-string (slurp "budget-private/config.edn")))