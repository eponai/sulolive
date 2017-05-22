(ns eponai.repl
  (:require [eponai.server.core :as core]
            [eponai.server.system :as system]
            [clojure.tools.namespace.repl :as ns.repl]
            [reloaded.repl :refer [system start stop go]]
            [taoensso.timbre :as timbre :refer [debug]]
            [ns-tracker.core :refer [ns-tracker]])
  (:import (java.util.concurrent LinkedBlockingQueue)))

(ns.repl/disable-reload!)
(ns.repl/set-refresh-dirs "src" "test" "env")

;; Reloading code inspired by:
;; Pedestal - auto-server-reload
;; https://github.com/pedestal/samples/blob/master/auto-reload-server/dev/dev.clj#L38
(defn- reloader [dirs]
  (let [tracker (ns-tracker dirs)
        reload-ns (fn [ns-sym]
                    (try
                      (require ns-sym :reload)
                      (catch Throwable e
                        (.printStackTrace e))))]
    (fn []
      (let [modified-namespaces (tracker)]
        (when (seq modified-namespaces)
          (reloaded.repl/suspend))
        (try
          (doseq [ns-sym modified-namespaces]
            (reload-ns ns-sym))
          (finally
            (when (seq modified-namespaces)
              (reloaded.repl/resume)
              (system/resume-requests system))))))))

(let [reloader-atom (atom nil)]
  (defn start-reloading
    "Starts a thread that reloads our code everytime a namespace is changed.
     Our system is suspended and resumed between namespace reloads.

     Stop the reloading by calling (stop-reloading)."
    []
    (if-let [stop-reload! @reloader-atom]
      stop-reload!
      (let [done (atom false)
            reload! (reloader ["src" "test" "env"])]
        (doto
          (Thread. (fn []
                     (while (not @done)
                       (try
                         (reload!)
                         (catch Throwable e
                           (debug "Error when reloading: " e)))
                       (Thread/sleep 500))))
          (.setDaemon true)
          (.start))
        (reset! reloader-atom (fn [] (reset! done false)))
        @reloader-atom)))

  (defn stop-reloading []
    (when-let [stop-reload! @reloader-atom]
      (stop-reload!))
    (reset! reloader-atom nil)))


;; END Reloading code

(defn init []
  (prn "****************************************************")
  (prn "                REPL TIPS AND TRICKS:")
  (prn " ** System Commands: ")
  (prn " [init-system system start stop go reset reset-all]")
  (prn " ** Other: ")
  (prn " (set-debug) - set logger level to debug")
  (prn " (set-trace) - set logger level to trace")
  (prn " (set-level level) - set logger level to level")
  (prn " eponai.server.core is aliased :as core")
  (prn "****************************************************")
  (reloaded.repl/set-init! #(core/make-dev-system)))

(defn init-system []
  (reloaded.repl/init)
  (start-reloading))

;; start-server was the old command we've gotten used to:
(defn start-server []
  (init-system)
  (start))

(defn set-level [level]
  (timbre/set-level! level))

(defn set-trace []
  (set-level :trace))

(defn set-debug []
  (set-level :debug))

(defn- with-no-reload [f]
  (stop-reloading)
  (try
    (f)
    (catch Throwable t
      (.printStackTrace t)
      (start-reloading))))

(defn reset []
  (with-no-reload #(reloaded.repl/reset)))

(defn reset-all []
  (with-no-reload #(reloaded.repl/reset-all)))
