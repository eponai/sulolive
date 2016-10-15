(ns eponai.fullstack.utils
  (:require [clojure.core.async :as async]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [clojure.pprint :as pprint :refer [write-out with-pprint-dispatch]]
            [clansi.core :as clansi]
            [aprint.utils :refer :all]
            [aprint.tty :refer [tty-width clear-screen]]
            [aprint.dispatch :refer [color-dispatch]]
            [aprint.writer :refer [with-awesome-writer]]))

(defn take-with-timeout [chan label & [timeout-millis throw?]]
  {:pre [(string? label)]}
  (let [timeout (or timeout-millis 5000)
        throw? (or throw? (nil? throw?))
        [v c] (async/alts!! [chan (async/timeout timeout)])]
    (if (= c chan)
      v
      (cond-> (ex-info (str "Timed out awaiting " label)
                       {:label   label
                        :timeout timeout
                        :throw?  throw?})
              throw?
              (throw)))))

(defn less-loud-output-fn
  "Like timbre/default-output-fn, but it doesn't print timestamp and hostname."
  ([data] (less-loud-output-fn nil data))
  ([opts data]                                              ; For partials
   (let [{:keys [no-stacktrace? stacktrace-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str hostname_
                 timestamp_ ?line vargs_]} data]
     ;; #+clj (force timestamp_) #+clj " "
     ;; #+clj (force hostname_)  #+clj " "
     (if (and (not no-stacktrace?) ?err)
       (str
         (str/upper-case (name level))
         (str "[" (or ?ns-str "?") ":" (or ?line "?") "] - ")
         (force msg_)
         (timbre/stacktrace ?err opts))
       [(str/upper-case (name level))
        (str "[" (or ?ns-str "?") ":" (or ?line "?") "] - ")
        (if (#{:debug :trace :warn} level)
          (force msg_)
          @vargs_)]))))

(defn aprint
  "like aprint.core/aprint, but it doesn't clear the console."
  [object writer]
  (binding [pprint/*print-right-margin* (tty-width)]
    (pprint/with-pprint-dispatch
      color-dispatch
      (with-awesome-writer
        writer
        (binding [pprint/*print-pretty* true]
          (binding-map (if (or (not (= pprint/*print-base* 10)) pprint/*print-radix*) {#'pr #'pprint/pr-with-base} {})
                       (write-out object)))
        (if (not (= 0 (#'pprint/get-column *out*)))
          (prn))))))

(defn with-less-loud-logger [f]
  (let [config# timbre/*config*
        sync-appender# {:sync      true
                        :enabled?  true
                        :output-fn less-loud-output-fn
                        :fn        (fn [{:keys [output-fn ?err output_] :as data}]
                                     (if ?err
                                       (do (print (force output_))
                                           (print \newline)
                                           (flush))
                                       (aprint (output-fn data) *out*)))}]
    (try
      (timbre/set-config!
        (assoc config# :appenders {:sync-appender sync-appender#}))
      (timbre/info "Temporarily set logger to less loud logger")
      (f)
      (finally
        (timbre/set-config! config#)))))

