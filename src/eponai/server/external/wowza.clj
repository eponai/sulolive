(ns eponai.server.external.wowza)

(defprotocol IWowza
  (jwt-secret [this])
  (subscriber-url [this])
  (publisher-url [this]))

(defrecord Wowza [secret subscriber-url publisher-url]
  IWowza
  (jwt-secret [this] secret)
  (subscriber-url [this] subscriber-url)
  (publisher-url [this] publisher-url))

(defn wowza [m]
  (map->Wowza m))

(defn wowza-stub [{:keys [secret]}]
  (reify IWowza
    (jwt-secret [this] (or secret "secret"))
    (subscriber-url [this] "http://localhost:1935")
    (publisher-url [this] "rtmp://localhost:1935/live")))
