(ns eponai.web.utils
  (:require
    [taoensso.timbre :refer [debug]]))

(defn enter-pressed? [^js/Event e]
  (= 13 (.-keyCode e)))

(def breakpoints
  {:small   0
   :medium  640
   :large   1024
   :xlarge  1200
   :xxlarge 1440})

(defn bp-compare [bp other & [compare-fn]]
  (let [c (or compare-fn >)]
    (c (get breakpoints bp) (get breakpoints other))))

(defn breakpoint [size]
  (cond (> size 1440) :xxlarge
        (> size 1200) :xlarge
        (> size 1024) :large
        (> size 640) :medium
        :else :small))

(defn add-class-to-element [el class]
  (when (some? el)
    (let [old-classname (.-className el)]
      (debug "oldclass: " old-classname)
      (set! (.-className el) (clojure.string/join " " [old-classname class])))))

(defn remove-class-to-element [el class]
  (when (some? el)
    (let [old-classname (str " " (.-className el) " ")
          new-classname (clojure.string/replace old-classname (str " " class) " ")]
      (set! (.-className el) (clojure.string/trim new-classname)))))

(defn elements-by-class
  ([classname]
    (elements-by-class js/document classname))
  ([^js/document el classname]
   (array-seq (.getElementsByClassName el classname))))

(defn ^js/HTMLElement element-by-id [id]
  (.getElementById js/document id))

(defn elements-by-name [^js/document n]
  (array-seq (.getElementsByName n)))

(defn elements-by-tagname [n]
  (array-seq (.getElementsByTagName js/document n)))

(defn input-values-by-class
  ([classname]
    (input-values-by-class js/document classname))
  ([el classname]
   (map (fn [^js/HTMLInputElement e] (.-value e)) (elements-by-class el classname))))

(defn first-input-value-by-class
  ([classname]
    (first-input-value-by-class js/document classname))
  ([el classname]
   (not-empty (first (input-values-by-class el classname)))))

(defn selected-value-by-id [id]
  (let [el (element-by-id id)]
    (when el
      (not-empty (.-value el)))))

(defn input-value-by-id [id]
  (let [el (element-by-id id)]
    (when el
      (.-value el))))

(defn input-value-or-nil-by-id [id]
  (not-empty (input-value-by-id id)))

(defn input-checked-by-id? [id]
  (let [el (element-by-id id)]
    (when el
      (.-checked el))))

(defn fullscreen-element []
  ;(let [document js/document])
  (or
    (.-fullscreenElement js/document)
    (.-webkitFullscreenElement js/document)
    (.-mozFullScreenElement js/document)))


(defn exit-fullscreen []
  ;(let [document js/document])
  (cond
    ;; Standard fullscreen handler
    (.-exitFullscreen js/document)
    (.exitFullscreen js/document)

    ;; Mozilla fallback
    (.-mozCancelFullscreen js/document)
    (.mozCancelFullscreen js/document)

    ;; Safari & Chrome fallback
    (.-webkitExitFullscreen js/document)
    (.webkitExitFullscreen js/document)))

(defn request-fullscreen [^js/Element el]
  (cond
    ;; Standard fullscreen handler
    (.-requestFullscreen el)
    (.requestFullscreen el)

    ;; Mozilla fallback
    (.-mozRequestFullscreen el)
    (.mozRequestFullscreen el)

    ;; Safari & Chrome fallback
    (.-webkitRequestFullscreen el)
    (.webkitRequestFullscreen el)))

