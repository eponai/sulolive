(ns flipmunks.budget.core
  (:gen-class)
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [clojure.data.json :as json]))

(def test-data {:currency :USD
                :dates {2015 {1 {3 {:purchases [{:name "coffee" :cost {:currency :LEK :price 150}}]
                                    :rates {:LEK 0.0081
                                            :SEK 0.1213}}
                                 4 {:purchases [{:name "coffee" :cost {:currency :LEK :price 150}}, {:name "market" :cost {:currency :LEK :price 300}}]
                                    :rates {:LEK 0.0081
                                            :SEK 0.1213}}}}}})
(defn deep-merge
  "Returns a map that consists of the rest of the maps merged onto the first.\n
  If a key occurs in more than one map, the mapping from\n  the latter (left-to-right) will be the mapping in the result.\n
  If a key holding a map value occurs in more than one map, the mapping(s) from the latter (left-to-right)\n
  will be combined with the mapping in the result by calling (deep-merge val-in-result val-in-latter)."
  [& maps]
  (when (some identity maps)
    (let [merge-entry (fn [m [k v]]
                        (if (and (map? v) (map? (get m k)))                  ; might need to check if (get m k) is a map as well
                          (assoc m k (deep-merge (get m k) v))
                          (assoc m k v)))
          merge1 (fn [m1 m2]
                   (reduce merge-entry m1 m2))]
      (reduce merge1 maps))))

(defroutes app-routes
           (context "/entries" [] (defroutes entries-routes
                                             (GET "/" [] (str test-data))
                                             (POST "/" {body :body} (str body))))
           (route/not-found "Not Found"))

(def app
   (middleware/wrap-json-response (middleware/wrap-json-body (handler/api app-routes) {:keywords? true})))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))