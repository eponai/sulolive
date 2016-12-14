(ns eponai.common.ui.elements.photo
  (:require
    [clojure.string :as s]
    [om.dom :as dom]
    [taoensso.timbre :refer [debug error warn]]))

;; Map Keyword -> CSS classes
(def all-styles
  {::photo     "photo"
   ::container "photo-container"
   ::square    "square"
   ::thumbnail "thumbnail"
   ::header    "header"
   ::collage   "collage"
   ::cover     "cover"})

;; Utils
(defn- keys->css-class
  "Convert keywords to string of classnames to use for :className in element options.

  Valid keywords are found in eponai.common.ui.elements.photo/all-styles"
  [styles]
  (when-not (every? #(some? (get all-styles %)) styles)
    (let [unexpected (filter #(nil? (get all-styles %)) styles)]
      (warn "Ignoring unexpected CSS class keys " unexpected)))

  (s/join " " (map #(get all-styles %) styles)))

;; Element helper functions
(defn- photo* [{:keys [url formats]} & content]
  (apply dom/div
         #js {:className (keys->css-class (conj formats ::photo))
              #?@(:cljs [:style #js {:backgroundImage (str "url(" url ")")}])}
         content))

(defn- photo-container [{:keys [formats]} & content]
  (dom/div #js {:className (keys->css-class (conj formats ::container))}
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
             :formats [::square]})))

(defn thumbail [url]
  (photo-container
    nil
    (photo* {:url     url
             :formats [::square ::thumbnail]})))

(defn header [url]
  (photo* {:url url
           :formats [::header]}))

(defn cover [url & content]
  (when-not (string? url) (error "Invalid photo URL type. Cover expects URL string as first argument. Got URL: " url))
  (photo-container
    nil
    (apply photo* {:url url
                   :formats [::cover]}
           content)))

(defn collage [urls]
  (when-not (every? string? urls) (error "Invalid photo URL type. Collage expects collection of URL strings. Got URLs: " urls))
  (apply photo-container
         {:formats [::collage]}
         (mapcat
           (fn [[large mini-1 mini-2]]
             [(photo* {:url     large
                       :formats [::square]})
              (photo-container nil
                               (photo* {:url mini-1})
                               (photo* {:url mini-2}))])
                 (partition 3 urls))))