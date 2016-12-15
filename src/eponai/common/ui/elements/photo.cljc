(ns eponai.common.ui.elements.photo
  (:require
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.dom :as dom]
    [taoensso.timbre :refer [debug error warn]]))

;; Element helper functions
(defn- photo* [{:keys [src classes]} & content]
  (if-not (string? src)
    (warn "Ignoring invalid photo src type, expecting a URL string. Got src: " src)
    (apply dom/div
           {:classes (conj classes ::css/photo)
            #?@(:cljs [:style {:backgroundImage (str "url(" src ")")}])}
           content)))

(defn- photo-container [opts & content]
  (apply dom/div (css/add-class ::css/photo-container opts) content))

;; Functiosn for creating elements in the UI
(defn photo [url]
  (photo-container
    nil
    (photo* {:url url})))

(defn square [opts]
  (photo-container
    nil
    (photo* (css/add-class ::css/photo-square opts))))

(defn thumbail [opts]
  (photo-container
    nil
    (photo* (->> opts
                 (css/add-class ::css/photo-square)
                 (css/add-class ::css/photo-thumbnail)))))

(defn header [opts]
  (photo* (css/add-class ::css/photo-header opts)))

(defn cover [{:keys [src] :as opts} & content]
  (when-not (string? src)
    (error "Invalid photo URL type. Cover expects URL string as first argument. Got URL: " src))
  (photo-container
    nil
    (apply photo* (css/add-class ::css/photo-cover opts)
           content)))

(defn collage [urls]
  (when-not (every? string? urls)
    (error "Invalid photo URL type. Collage expects collection of URL strings. Got URLs: " urls))
  (apply photo-container
         {:classes [:css/photo-collage]}
         (mapcat
           (fn [[large mini-1 mini-2]]
             [(photo* {:url     large
                       :classes [::css/photo-square]})
              (photo-container nil
                               (photo* {:url mini-1})
                               (photo* {:url mini-2}))])
                 (partition 3 urls))))