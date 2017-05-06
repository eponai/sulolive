(ns eponai.common.photos
  (:require
    [clojure.string :as string]
    [taoensso.timbre :refer [debug]]))

(def transformations
  {:transformation/micro           "micro"
   :transformation/thumbnail-tiny  "thumbnail-tiny"
   :transformation/thumbnail       "thumbnail"
   :transformation/thumbnail-large "thumbnail-large"
   :transformation/preview         "preview"})

(def storage-host "http://res.cloudinary.com/sulolive")

(def transformation-path)
(defn transformation-param
  "Get transformation parameter for URL given the key"
  [k]
  (when k
    (str "t_" (get transformations k))))

(defn transform [public-id & [transformation]]
  (let [url (string/join "/" (into [] (remove nil? [storage-host "image/upload" (transformation-param transformation) (str public-id ".jpg")])))]
    url))