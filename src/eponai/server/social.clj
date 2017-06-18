(ns eponai.server.social
  (:require [eponai.common.photos :as photos]
            [eponai.common.database :as db]
            [eponai.common :as c]
            [eponai.server.external.host :as host]
            [eponai.common.stream :as stream]
            [eponai.server.external.wowza :as wowza]
            [eponai.common.routes :as routes]
            [clojure.string :as string]
            [eponai.common.format :as f]))

(defn- store [{:keys [state system]} store-id]
  (when-let [store-id (c/parse-long-safe store-id)]
    (when-let [store (db/pull (db/db state)
                              [{:store/profile [:store.profile/name
                                                :store.profile/tagline
                                                {:store.profile/photo [:photo/id]}]}
                               {:stream/_store [:stream/state]}]
                              store-id)]
      (let [{:keys  [store/profile]
             stream :stream/_store} store
            image (photos/transform (get-in profile [:store.profile/photo :photo/id]) :transformation/thumbnail)
            stream-url (when (= :stream.state/live
                                (:stream/state stream))
                         (stream/wowza-live-stream-url (wowza/subscriber-url (:system/wowza system))
                                                       (stream/stream-id store)))
            server-host (host/webserver-url (:system/server-address system))]
        (cond->> {:facebook {:fb:app_id      "936364773079066"
                             :og:title       (:store.profile/name profile)
                             :og:type        "video.other"
                             :og:description (or (:store.profile/tagline profile)
                                                 (:store.profile/name profile))
                             :og:image       image
                             :og:url         (str server-host (routes/path :store {:store-id store-id}))
                             }
                  :twitter  {:twitter:card        "summary_large_image"
                             :twitter:site        "@sulolive"
                             :twitter:title       (:store.profile/name profile)
                             :twitter:description (or (:store.profile/tagline profile)
                                                      (:store.profile/name profile))
                             :twitter:image       image}}

                 (some? stream-url)
                 (merge-with merge {:facebook {:og:video            stream-url
                                               :og:video:secure_url stream-url}
                                    :twitter  {:twitter:player:stream stream-url}}))))))

(defn- product [{:keys [state system]} product-id]
  (when-let [product-id (c/parse-long-safe product-id)]
    (when-let [product (db/pull (db/db state)
                                [:store.item/name
                                 :store.item/price
                                 :store.item/description
                                 {:store.item/photos [:store.item.photo/index
                                                      {:store.item.photo/photo [:photo/id]}]}]
                                product-id)]
      (let [photo (first (sort-by :store.item.photo/index (:store.item/photos product)))
            image (photos/transform (get-in photo [:store.item.photo/photo :photo/id]) :transformation/preview)
            server-host (host/webserver-url (:system/server-address system))
            description-html (f/bytes->str (:store.item/description product))
            description-text (if description-html
                               (or (not-empty (string/replace description-html #"<(?:.|\n)*?>" ""))
                                   (:store.item/name product))
                               (:store.item/name product))]
        {:facebook {:fb:app_id      "936364773079066"
                    :og:title       (:store.item/name product)
                    :og:type        "product"
                    :og:description description-text
                    :og:image       image
                    :og:url         (str server-host (routes/path :product {:product-id product-id}))}
         :twitter  {:twitter:card        "summary_large_image"
                    :twitter:site        "@sulolive"
                    :twitter:title       (:store.item/name product)
                    :twitter:description description-text
                    :twitter:image       image}}))))

(defn- default [{:keys [state system]}]
  (let [title "Your local marketplace online - SULO Live"
        description " Global change starts local. Shop and hangout LIVE with your favourite local brands and people from your city!"
        image (photos/transform "static/landing-social" :transformation/preview)
        server-host (host/webserver-url (:system/server-address system))]
    {:facebook {:fb:app_id      "936364773079066"
                :og:title       title
                ;:og:type                "product"
                :og:description description
                :og:image       image
                :og:url         (str server-host)}
     :twitter  {:twitter:card        "summary_large_image"
                :twitter:site        "@sulolive"
                :twitter:title       title
                :twitter:description description
                :twitter:image       image}}))

(defn share-objects [{:keys [route state route-params system] :as env}]
  (cond (= route :store)
        (store env (:store-id route-params))
        (= route :product)
        (product env (:product-id route-params))
        :else
        (default env)))