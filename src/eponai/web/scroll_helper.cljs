(ns eponai.web.scroll-helper
  (:require [goog.object :as gobj]
            [taoensso.timbre :refer [debug]]
            [datascript.core :as datascript]))

(def scroll-restoration-timeout-ms 500)

(defprotocol IScrollOnPushState
  (scroll-on-push-state [this]))

(let [timeout-handle (atom nil)]
  (defn try-to-scroll-to
    ([scroll-target] (try-to-scroll-to scroll-target (iterate #(min 30 (+ 5 %)) 0)))
    ([{:keys [x y latest-time-to-try] :as scroll-target} timeouts]
      ;; Stop any previous calls to "try-to-scroll-to".
     (when-let [timeout-handle @timeout-handle]
       (js/clearTimeout timeout-handle))
     (let [body js/document.body
           html js/document.documentElement
           ;; Taken from http://stackoverflow.com/a/1147768
           document-width (max (.-scrollWidth body)
                               (.-offsetWidth body)
                               (.-clientWidth html)
                               (.-scrollWidth html)
                               (.-offsetWidth html))
           document-height (max (.-scrollHeight body)
                                (.-offsetHeight body)
                                (.-clientHeight html)
                                (.-scrollHeight html)
                                (.-offsetHeight html))
           should-scroll-to? (or (and (>= (- document-width (.-innerWidth js/window) x))
                                      (>= (- document-height (.-innerHeight js/window) y)))
                                 (> (.now js/Date) latest-time-to-try))]
       (if should-scroll-to?
         (.scrollTo js/window x y)
         (reset! timeout-handle (js/setTimeout #(try-to-scroll-to scroll-target (rest timeouts))
                                               (first timeouts))))))))

;; Inspired by
;; https://github.com/brigade/delayed-scroll-restoration-polyfill/blob/1aa90ef6ff9c3b295da4574532e073e057fc6c0a/index.es6.js#L57-L72

(defn create-functions [on-push-state]
  (when (.-pushState js/history)
    (let [original-push-state (.-pushState js/history)
          original-replace-state (.-replaceState js/history)
          new-push-state
          (fn []
            (let [new-state-of-current-page (js/Object.assign
                                              #js {}
                                              (.-state js/history)
                                              #js {:__uuid    (datascript/squuid)
                                                   :__scrollX (.-scrollX js/window)
                                                   :__scrollY (.-scrollY js/window)})]
              (debug "push-state: " new-state-of-current-page)
              (on-push-state)
              (.call original-replace-state js/history new-state-of-current-page "")
              (.apply original-push-state js/history (js-arguments))))
          new-replace-state
          (fn [state & other-args]
            (let [old-state (.-state js/history)
                  new-state (js/Object.assign
                              #js {}
                              #js {:__uuid    (datascript/squuid)
                                   :__scrollX (when old-state
                                                (gobj/get old-state "__scrollX"))
                                   :__scrollY (when old-state
                                                (gobj/get old-state "__scrollY"))}
                              state)
                  replace-state-args (.concat #js [new-state] (into-array other-args))]
              (debug "replace-state: " new-state)
              (.apply original-replace-state js/history replace-state-args)))
          ]
      {:new          {:replace-state new-replace-state
                      :push-state    new-push-state}
       :old          {:replace-state original-replace-state
                      :push-state    original-push-state}})))

(defonce scroll-functions (atom nil))

(defn- set-history-functions! [{:keys [push-state replace-state]}]
  (set! (.-pushState js/history) push-state)
  (set! (.-replaceState js/history) replace-state))

(defn init-scroll! []
  (when-let [{:keys [old]} @scroll-functions]
    (set-history-functions! old))
  (set! (.-scrollRestoration js/history) "auto")

  (let [on-push-state (atom false)
        {:keys [new] :as functions} (create-functions (fn [] (reset! on-push-state true)))]
    (reset! scroll-functions functions)
    (set-history-functions! new)

    (reify IScrollOnPushState
      (scroll-on-push-state [this]
        (when @on-push-state
          (reset! on-push-state false)
          (try-to-scroll-to {:x 0 :y 0 :latest-time-to-try (+ (.now js/Date) scroll-restoration-timeout-ms)}))))))
