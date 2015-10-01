(ns flipmunks.budget.backend
  (:require [ajax.core :as http]))

(def testdata {2015 {1 {1 {:purchases [{:name "coffee" :cost {:currency "LEK"
                                                              :price 400}}
                                       {:name "dinner" :cost {:currency "LEK"
                                                              :price 1000}}]
                           :rates {"LEK" 0.0081}}
                        2 {:purchases [{:name "lunch" :cost {:currency "LEK"
                                                             :price 600}}]
                           :rates {"LEK" 0.0081}}}}})

(defn success? [req]
  false)

(defn data [req]
  (throw js/Error. "not yet implemented"))

(defn get-dates []
  (prn "TODO: implement the backend so we might get data..?")
  (let [req (http/GET "/the-data")]
    (if (success? req)
      (data req)
      (do 
        (prn "could not get data from the backend. Using test data")
        testdata))))

