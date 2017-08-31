(ns eponai.web.seo
  (:require
    [eponai.common.photos :as photos]
    [eponai.common.database :as db]
    [eponai.common.stream :as stream]
    [eponai.common.routes :as routes]
    [eponai.common.format :as common.format]
    [eponai.client.routes :as client.routes]
    [eponai.web.header :as header]
    #?(:clj
    [eponai.server.external.host :as host])
    #?(:clj
    [eponai.server.external.wowza :as wowza])
    [eponai.common.shared :as shared]
    [eponai.client.client-env :as client-env]))

(defn- p-meta-tag
  "Makes it easy to create a meta tag map with property and content."
  [property-key content]
  (when (some? content)
    (header/meta-tag :property (name property-key)
                     :content content)))
(defn- title-tag
  "Adds the :og:title property to the title for facebook."
  [title]
  (header/title-tag title :property :og:title))

(defn- description-tag
  "Adds the :og:description property to the title for facebook."
  [description]
  (header/description-tag description :property :og:description))



(defn- server-url [{:keys [system]}]
  (str #?(:clj  (host/webserver-url (:system/server-address system))
          :cljs js/window.location.origin)))

(defn- wowza-url [{:keys [system reconciler]}]
  #?(:clj  (-> (:system/client-env system)
               (client-env/get-key :wowza-subscriber-url))
     :cljs (-> (shared/by-key reconciler :shared/client-env)
               (client-env/get-key :wowza-subscriber-url))))

(defn- default-data
  "This data is ALWAYS included if not overwritten by other functions."
  [{:keys [db route-map system reconciler]}]
  (let [title "Shop local live - SULO Live"
        description "Global change starts local. Shop and hangout LIVE with your favourite local brands and people from your city!"
        image (photos/transform "static/products" :transformation/preview)]
    [
     ;; Title
     (title-tag title)
     (description-tag description)
     ;; Facebook
     (p-meta-tag :fb:app_id "936364773079066")
     (p-meta-tag :og:image image)
     (p-meta-tag :og:url (str (server-url {:system system}) (routes/map->url route-map)))
     ;; Twitter
     (p-meta-tag :twitter:card "summary_large_image")
     (p-meta-tag :twitter:site "@sulolive")]))

(defn- store [{:keys [db route-map system reconciler]}]
  (when-let [store (some->> (get-in route-map [:route-params :store-id])
                            (db/store-id->dbid db)
                            (db/entity db))]
    (let [{:keys  [store/profile]} store
          image (photos/transform (get-in profile [:store.profile/photo :photo/id]) :transformation/thumbnail)
          stream-url (when (= :stream.state/live (get-in store [:stream/_store :stream/state]))
                       (stream/wowza-live-stream-url (wowza-url {:system system :reconciler reconciler})
                                                     (stream/stream-id store)))]
      [
       ;; Title
       (title-tag (:store.profile/name profile))
       (description-tag (:store.profile/tagline profile))
       ;; Facebook
       (p-meta-tag :og:title (:store.profile/name profile))
       (p-meta-tag :og:type "video.other")
       (p-meta-tag :og:description (or (:store.profile/tagline profile)
                                       (:store.profile/name profile)))
       ;; Twitter
       (p-meta-tag :og:image image)
       ;; When video
       ;; Facebook
       (p-meta-tag :og:video stream-url)
       (p-meta-tag :og:video:secure_url stream-url)
       ;; Twitter
       (p-meta-tag :twitter:player:stream stream-url)])))

(defn product [{:keys [route-map db]}]
  (when-let [product (some->> route-map
                                 (client.routes/with-normalized-route-params db)
                                 :route-params
                                 :product-id
                                 (db/entity db))]
    (let [photo (first (sort-by :store.item.photo/index (:store.item/photos product)))
          image (photos/transform (get-in photo [:store.item.photo/photo :photo/id]) :transformation/preview)
          description-html (common.format/bytes->str (:store.item/description product))
          description-text (if description-html
                             (or (not-empty (clojure.string/replace description-html #"<(?:.|\n)*?>" ""))
                                 (:store.item/name product))
                             (:store.item/name product))
          product-name (:store.item/name product)]
      [
       ;; Title
       (title-tag product-name)
       (description-tag description-text)
       ;; Facebook
       (when (some? product-name)
         (p-meta-tag :og:title (str product-name " - SULO Live")))
       (p-meta-tag :og:type        "product")
       (p-meta-tag :og:description description-text)
       (p-meta-tag :og:image       image)
       ;; Twitter
       ])))

(defn head-meta-data
  "Takes db, route-map and system (for clj) or reconciler (for cljs).
  Returns head-meta-data to be processed by eponai.web.header"
  [{:keys [db route-map system reconciler] :as params}]
  (->> (concat (default-data params)
               (condp = (:route route-map)
                 :store (store params)
                 :product (product params)
                 []))
       (filter some?)))
