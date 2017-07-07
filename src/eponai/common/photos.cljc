(ns eponai.common.photos
  (:require
    #?(:clj [clj-http.client :as http]
       :cljs [cljs-http.client :as http])
            [clojure.string :as string]
            [taoensso.timbre :refer [debug]]
            [eponai.common.shared :as shared]))

(def transformations
  {:transformation/micro           "micro"
   :transformation/thumbnail-tiny  "thumbnail-tiny"
   :transformation/thumbnail       "thumbnail"
   :transformation/thumbnail-large "thumbnail-large"
   :transformation/preview         "preview"
   :transformation/cover           "cover"
   :transformation/banner          "city-banner"})

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
        t (transformation-param transformation)
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

(defprotocol IPhotos
  (mini [this photo-id opts])
  (main [this photo-id opts]))

(defmethod shared/shared-component [:shared/photos :env/prod]
  [_ _ _]
  (reify IPhotos
    (mini [this photo-id {:keys [ext]}]
      (transform photo-id :transformation/micro ext))
    (main [this photo-id {:keys [ext transformation]}]
      (transform photo-id transformation ext))))

(defmethod shared/shared-component [:shared/photos :env/dev]
  [_ _ _]
  (let [url-fn (fn [photo-id]
                 (let [static-key (last (string/split photo-id #"/"))
                       photo-key (or (some #{"storefront-cover-2" "storefront-2"} [static-key]) "cat-profile-3")]
                   (str "/assets/img/" photo-key ".jpg")))]
    (reify IPhotos
      (mini [this photo-id {:keys [ext]}]
        (url-fn photo-id))
      (main [this photo-id {:keys [ext transformation]}]
        ;(transform photo-id :transformation/micro ext)
        (url-fn photo-id)
        ))))