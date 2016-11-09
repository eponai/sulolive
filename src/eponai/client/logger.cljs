(ns eponai.client.logger
  (:require [clojure.string :as str]
            [goog.debug]
            [goog.log :as glog]
            [taoensso.timbre :as timbre]))

(def goog-root-logger
  (glog/getLogger goog.debug.Logger.ROOT_LOGGER_NAME))

(def levels {:severe  goog.debug.Logger.Level.SEVERE
             :warning goog.debug.Logger.Level.WARNING
             :info    goog.debug.Logger.Level.INFO
             :config  goog.debug.Logger.Level.CONFIG
             :fine    goog.debug.Logger.Level.FINE
             :finer   goog.debug.Logger.Level.FINER
             :finest  goog.debug.Logger.Level.FINEST})

(defn log-to-console! []
  (.setCapturing (goog.debug.Console.) true))

(defn set-goog-level! [level]
  (.setLevel goog-root-logger (get levels level (:info levels))))

;; Adjust this level to debug goog library.
;; (set-goog-level! :fine)


;; Both the appender and output fun is copied and tweaked form github:
;; https://github.com/ptaoussanis/timbre/blob/a26ecc6a96475cb80026f55d1467fb490658ab8d/src/taoensso/timbre/appenders/core.cljx#L137
;; https://github.com/ptaoussanis/timbre/blob/a26ecc6a96475cb80026f55d1467fb490658ab8d/src/taoensso/timbre.cljx#L34

(defn eponai-output-fn
  ([data] (eponai-output-fn nil data))
  ([{:keys [no-stacktrace?] :as opts} data]
   (let [{:keys [level ?err_ ?ns-str vargs_]} data]
     (into-array
       (cond-> (concat [(str (str/upper-case (name level))
                             " "
                             "[" (or ?ns-str "?ns") "] - ")]
                       @vargs_)

               (not no-stacktrace?)
               (concat [(str "\n" (str @?err_ opts))]))))))

(defn eponai-appender
  []
  (when-let [have-logger? (and (exists? js/console) (.-log js/console))]
    (let [have-warn-logger?  (.-warn  js/console)
          have-error-logger? (.-error js/console)
          level->logger {:fatal (if have-error-logger? :error :info)
                         :error (if have-error-logger? :error :info)
                         :warn  (if have-warn-logger?  :warn  :info)}]
      {:enabled?   true
       :async?     false
       :min-level  nil
       :rate-limit nil
       :output-fn  :inherit
       :fn         (fn [data]
                     (let [{:keys [level output-fn]} data
                           output (output-fn data)
                           log-fn (case (level->logger level)
                                    :error (.-error js/console)
                                    :warn (.-warn js/console)
                                    (.-log js/console))]
                       (.apply log-fn js/console output)))})))

(defn install-logger!
  "Installs our logger."
  []
  (let [appender (eponai-appender)]
    ;; Using timbre/swap-config! instead of timbre/merge-config!
    ;; since merge-config! doesn't work?
    (timbre/swap-config! merge
                         {:output-fn   eponai-output-fn
                          :appenders   {:eponai appender}
                          :appender    appender
                          :appender-id :eponai})))
