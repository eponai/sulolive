(ns eponai.common.ui.elements.photo
  (:require
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [taoensso.timbre :refer [debug error warn]]))

;; Element helper functions
(defn- photo* [{:keys [url formats]} & content]
  (apply dom/div
         #js {:className (css/keys->class (conj formats ::css/photo))
              #?@(:cljs [:style #js {:backgroundImage (str "url(" url ")")}])}
         content))

(defn- photo-container [{:keys [formats]} & content]
  (dom/div #js {:className (css/keys->class (conj formats ::css/photo-container))}
    content))

;; Functiosn for creating elements in the UI
(defn photo [url]
  (photo-container
    nil
    (photo* {:url url})))

(defn square [url]
  (photo-container
    nil
    (photo* {:url url
             :formats [::css/photo-square]})))

(defn thumbail [url]
  (photo-container
    nil
    (photo* {:url     url
             :formats [::css/photo-square ::css/photo-thumbnail]})))

(defn header [url]
  (photo* {:url url
           :formats [::css/photo-header]}))

(defn cover [url & content]
  (when-not (string? url) (error "Invalid photo URL type. Cover expects URL string as first argument. Got URL: " url))
  (photo-container
    nil
    (apply photo* {:url url
                   :formats [::css/photo-cover]}
           content)))

(defn collage [urls]
  (when-not (every? string? urls) (error "Invalid photo URL type. Collage expects collection of URL strings. Got URLs: " urls))
  (apply photo-container
         {:formats [:css.photo/collage]}
         (mapcat
           (fn [[large mini-1 mini-2]]
             [(photo* {:url     large
                       :formats [::css/photo-square]})
              (photo-container nil
                               (photo* {:url mini-1})
                               (photo* {:url mini-2}))])
                 (partition 3 urls))))