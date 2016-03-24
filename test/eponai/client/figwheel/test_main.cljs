(ns eponai.client.figwheel.test-main
  (:require [eponai.client.tests :as tests]
            [eponai.client.utils :as utils]
            [cljs.test]
            [goog.dom :as gdom]))

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


(defn ^:export run []
  (utils/install-app)
  (if-let [report (tests/run)]
    (if (cljs.test/successful? report)
      (set-favicon :success)
      (set-favicon :failure))
    (set-favicon :warning)))

(defn reload-figwheel! []
  (run))
