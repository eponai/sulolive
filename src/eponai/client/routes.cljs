(ns eponai.client.routes
  (:require [bidi.bidi :as bidi]
            [clojure.string :as s]))

(def app-root "/app")

(defn- create-route [root paths]
  (letfn [(trim-separators [s]
            (let [s (str s)]
              (cond-> s
                      (s/starts-with? s "/") (->> rest (apply str))
                      (s/ends-with? s "/") (->> butlast (apply str)))))]
    (s/join "/" (cons root (map trim-separators paths)))))

(defn outside
  "Takes any number of paths and creates a path outside our app."
  [& paths]
  (create-route "" paths))

(defn inside
  "Takes any number of paths and creates a path inside our app."
  [& paths]
  (create-route app-root paths))

(def dashboard-routes
  {(bidi/alts "" "/") :route/dashboard
   "/dashboard"       {(bidi/alts "" "/" ["/" :budget-uuid]) :route/dashboard}})

(def routes [app-root (merge
                        dashboard-routes
                        {"/transactions" :route/transactions
                         "/settings"     :route/settings
                         "/subscribe"    :route/subscribe
                         "/widget/new"   :route/widget})])
