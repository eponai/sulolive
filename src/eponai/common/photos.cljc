(ns eponai.common.photos
  (:require
    #?(:clj [clj-http.client :as http]
       :cljs [cljs-http.client :as http])
            [clojure.string :as string]
            [taoensso.timbre :refer [debug]]))

(def transformations
  {:transformation/micro           "micro"
   :transformation/thumbnail-tiny  "thumbnail-tiny"
   :transformation/thumbnail       "thumbnail"
   :transformation/thumbnail-large "thumbnail-large"
   :transformation/preview         "preview"
   :transformation/cover           "cover"})

(def presets
  {:preset/product-photo "product-photo"
   :preset/cover-photo   "cover-photo"})

(def storage-host "https://res.cloudinary.com/sulolive")

(def api-host "https://api.cloudinary.com/v1_1")
(def cloud-name "sulolive")

(def transformation-path)
(defn transformation-param
  "Get transformation parameter for URL given the key"
  [k]
  (when k
    (str "t_" (get transformations k))))

(defn transform [public-id & [transformation file-ext]]
  (let [ext (or file-ext "jpg")
        t (when-not (= transformation :transformation/preview)
            (transformation-param transformation))
        url (string/join "/" (into [] (remove nil? [storage-host "image/upload" t (str public-id "." ext)])))]
    url))

(defn upload-photo [file preset]
  (let [resource-type "image"
        endpoint "upload"
        url (clojure.string/join "/" [api-host cloud-name resource-type endpoint])
        selected-preset (or preset :preset/product-photo)]
    (http/post url {:form-params       {:file          file
                                        :upload_preset (get presets selected-preset)}
                    :headers           {"X-Requested-With" "XMLHttpRequest"}
                    :with-credentials? false})))
