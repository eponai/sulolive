(ns eponai.fullstack.utils
  (:require [clojure.core.async :as async]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]))

(defn take-with-timeout [chan label & [timeout-millis]]
  {:pre [(string? label)]}
  (let [[v c] (async/alts!! [chan (async/timeout (or timeout-millis 5000))])]
    (when-not (= c chan)
      (throw (ex-info "client login timed out" {:where (str "awaiting " label)})))
    v))

(defn less-loud-output-fn
  "Like timbre/default-output-fn, but it doesn't print timestamp and hostname."
  ([data] (less-loud-output-fn nil data))
  ([opts data]                                              ; For partials
   (let [{:keys [no-stacktrace? stacktrace-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str hostname_
                 timestamp_ ?line]} data]
     (str
       ;; #+clj (force timestamp_) #+clj " "
       ;; #+clj (force hostname_)  #+clj " "
       (str/upper-case (name level)) " "
       "[" (or ?ns-str "?") ":" (or ?line "?") "] - "
       (force msg_)
       (when-not no-stacktrace?
         (when-let [err ?err]
           (str "\n" (timbre/stacktrace err opts))))))))

(defmacro with-less-loud-logger [& body]
  `(let [config# ~timbre/*config*
         sync-appender# {:sync      true
                         :enabled?  true
                         :output-fn ~less-loud-output-fn
                         :fn        (fn [m#]
                                      (print (force (:output_ m#)))
                                      (print \newline)
                                      (flush))}]
     (try
       (timbre/set-config!
         (assoc config# :appenders {:sync-appender sync-appender#}))
       (timbre/info "Temporarily set logger to less loud logger")
       (do ~@body)
       (finally
         (timbre/set-config! config#)))))
