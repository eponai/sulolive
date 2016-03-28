(ns eponai.client.figwheel.test-main
  (:require [eponai.client.tests :as tests]
            [eponai.client.utils :as utils]
            [cljs.test]
            [goog.dom :as gdom]))

(def favicons {:success {:type "image/png"
                         :href "/test/favicon/green.ico"}
               :failure {:type "image/gif"
                         :href "/test/favicon/red.ico"}})

(defn set-favicon [k]
  {:pre [(contains? favicons k)]}
  (gdom/setProperties (gdom/getElement "favicon")
                      (clj->js (get favicons k))))


(defn ^:export run []
  (utils/install-app)
  (let [report (tests/run)]
    (if (cljs.test/successful? report)
      (set-favicon :success)
      (set-favicon :failure))))

(defn reload-figwheel! []
  (run))
