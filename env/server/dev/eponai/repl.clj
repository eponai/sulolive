(ns eponai.repl
  (:require [eponai.server.core :as core]
            [clojure.tools.namespace.repl :as ns.repl]
            [reloaded.repl :refer [system start stop go]]
            [taoensso.timbre :as timbre :refer [debug]]
            [ns-tracker.core :refer [ns-tracker]])
  (:import (java.util.concurrent LinkedBlockingQueue)))

(ns.repl/disable-reload!)
(ns.repl/set-refresh-dirs "src" "test" "env")

;; Reloading code inspired by:
;; Ring wrap-reload
;; https://github.com/ring-clojure/ring/blob/master/ring-devel/src/ring/middleware/reload.clj#L7
;; Pedestal - auto-server-reload
;; https://github.com/pedestal/samples/blob/master/auto-reload-server/dev/dev.clj#L38
(defn- reloader [dirs reload-compile-errors?]
  (let [modified-namespaces (ns-tracker dirs)
        load-queue (LinkedBlockingQueue.)]
    (fn []
      (locking load-queue
        (doseq [ns-sym (modified-namespaces)]
          (.offer load-queue ns-sym))
        (when (seq load-queue)
          (reloaded.repl/suspend))
        (loop [reloaded? false]
          (if-let [ns-sym (.peek load-queue)]
            (do (if reload-compile-errors?
                  (do (require ns-sym :reload) (.remove load-queue))
                  (do (.remove load-queue) (require ns-sym :reload)))
                (recur true))
            (when reloaded?
              (reloaded.repl/resume))))))))

(let [reloader-atom (atom nil)]
  (defn start-reloading
    "Starts a thread that reloads our code everytime a namespace is changed.
     Our system is suspended and resumed between namespace reloads.

     Stop the reloading by calling (stop-reloading)."
    []
    (if-let [stop-reload! @reloader-atom]
      stop-reload!
      (let [done (atom false)
            reload! (reloader ["src" "test" "env"] true)]
        (doto
          (Thread. (fn []
                     (while (not @done)
                       (try
                         (reload!)
                         (catch Throwable e
                           (.printStackTrace e)
                           (debug "Error when reloading.. Will retry.")))
                       (Thread/sleep 300))))
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
  (reloaded.repl/set-init! #(core/main-debug)))

(defn init-system []
  (reloaded.repl/init)
  (start-reloading))

;; start-server was the old command we've gotten used to:
(def start-server init-system)

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
