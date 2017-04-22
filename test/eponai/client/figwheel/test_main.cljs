(ns eponai.client.figwheel.test-main
  (:require [eponai.client.tests :as tests]
            [eponai.client.devtools :as devtools]
            [cljs.test]
            [goog.dom :as gdom]
            [taoensso.timbre :as timbre :refer-macros [debug]]))

(def favicons {:success {:type "image/png"
                         :href "/test/favicon/green.ico"}
               :failure {:type "image/png"
                         :href "/test/favicon/red.ico"}
               :warning {:type "image/png"
                         :href "/test/favicon/warning.ico"}})

(defn set-favicon [k]
  {:pre [(contains? favicons k)]}
  (gdom/setProperties (gdom/getElement "favicon")
                      (clj->js (get favicons k))))

(defn run-tests []
  (if-let [report (tests/run)]
    (if (cljs.test/successful? report)
      (set-favicon :success)
      (set-favicon :failure))
    (set-favicon :warning)))

(defonce inited? (atom false))

(defn ^:export run []
  (devtools/install-app)
  (timbre/set-level! :error)
  (reset! inited? true)
  (run-tests))

(defn reload-figwheel! []
  (assert @inited?
          (str "Did not initiate the test space."
               " Need to call function " 'eponai.client.figwheel.test_main ".run() from this .html file."))
  (run-tests))
