(ns eponai.common.parser
  (:refer-clojure :exclude [merge])
  (:require [eponai.common.parser.read :as read]
            [eponai.common.parser.mutate :as mutate]

    #?(:clj  [om.next.server :as om]
       :cljs [om.next :as om])))

(defmulti merge (fn [_ k _] k))

(defn wrap-om-next-error-handler
  "For each :om.next/error, replace the value with only the error's
  ex-data.
  Throws an exception if the error did not have any data."
  [parser]
  (fn [& args]
    (let [ret (apply parser args)
          extract-ex-data-from-errors
          (fn [m k v]
            (if-not (:om.next/error v)
              m
              (update-in m [k :om.next/error]
                         (fn [err]
                           (if-let [data (ex-data err)]
                             (assoc data ::ex-message #?(:clj  (.getMessage err)
                                                         :cljs (.-message err)))
                             (throw (ex-info "Unable to get ex-data from error"
                                             {:error err
                                              :where ::wrap-om-next-error-handler
                                              :parsed-key   k
                                              :parsed-value v})))))))]
      (reduce-kv extract-ex-data-from-errors
                 ret
                 ret))))

(defn parser
  ([]
   (-> (om/parser {:read read/read :mutate mutate/mutate})
       #?(:clj wrap-om-next-error-handler
          :cljs identity))))
