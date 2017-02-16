(ns eponai.server.external.wowza)

(defprotocol IWowza
  (jwt-secret [this])
  (subscriber-url [this])
  (publisher-url [this]))

(defn wowza [{:keys [secret subscriber-url publisher-url]}]
  (reify
    IWowza
    (jwt-secret [this] secret)
    (subscriber-url [this] subscriber-url)
    (publisher-url [this] publisher-url)))

(defn wowza-stub []
  (reify IWowza
    (jwt-secret [this] "secret")
    (subscriber-url [this] "http://localhost:1935")
    (publisher-url [this] "rtmp://localhost:1935/live")))
