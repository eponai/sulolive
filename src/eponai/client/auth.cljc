(ns eponai.client.auth
  (:require [datascript.core :as d]
            [taoensso.timbre :refer [debug]]))

(defn has-active-user? [db]
  (throw (ex-info (str "TODO: Either extract as an option to where it's"
                       " being used, or implement this when we need it.")
                  {:todo :implement-function})))

