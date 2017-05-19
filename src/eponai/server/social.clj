(ns eponai.server.social
  (:require [eponai.common.photos :as photos]
            [eponai.common.database :as db]
            [eponai.common :as c]
            [eponai.server.external.host :as host]
            [eponai.common.stream :as stream]
            [eponai.server.external.wowza :as wowza]
            [eponai.common.routes :as routes]
            [clojure.string :as string]))

(defn- store [{:keys [state system]} store-id]
  (when-let [store-id (c/parse-long-safe store-id)]
    (when-let [store (db/pull (db/db state)
                              [{:store/profile [:store.profile/name
                                                :store.profile/tagline
                                                {:store.profile/photo [:photo/id]}]}
                               {:stream/_store [:stream/state]}]
                              store-id)]
      (let [{:keys [store/profile]
             stream :stream/_store} store
            image (photos/transform (get-in profile [:store.profile/photo :photo/id]) :transformation/full)
            stream-url (when (= :stream.state/live
                                (:stream/state stream))
                         (stream/wowza-live-stream-url (wowza/subscriber-url (:system/wowza system))
                                                       (stream/stream-id store)))
            server-host (host/webserver-url (:system/server-address system))]
        (cond-> {:fb:app_id           "936364773079066"
                 :og:title            (:store.profile/name profile)
                 :og:type             "video.other"
                 :og:description      (:store.profile/tagline profile "")
                 :og:image            image
                 :og:url              (str server-host (routes/path :store {:store-id store-id}))

                 :twitter:card        "summary_large_image"
                 :twitter:site        "@sulolive"
                 :twitter:title       (:store.profile/name profile)
                 :twitter:description (:store.profile/tagline profile "")
                 :twitter:image       image}

                (some? stream-url)
                (merge {:og:video            stream-url
                        :og:video:secure_url stream-url

                        :twitter:player:stream stream-url}))))))

(defn share-objects [{:keys [route state route-params system] :as env}]
  (cond (= route :store)
        (store env (:store-id route-params))))