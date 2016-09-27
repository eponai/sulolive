(ns eponai.web.playground
  (:require [eponai.common.parser :as parser]
            [eponai.client.backend :as backend]
            [eponai.client.utils :as utils]
            [eponai.client.remotes :as remotes]
            [eponai.web.app :as app]
            [eponai.web.routes :as routes]
            [eponai.web.homeless :as homeless]
            [eponai.web.ui.utils :as web.ui.utils]
            [taoensso.timbre :refer-macros [debug]]))

(defn parser-without-remote-mutations [parser]
  (let [parse-without-mutations (parser/parse-without-mutations parser)]
    (fn p
      [env query & [target]]
      (if (some? target)
        (parse-without-mutations env query target)
        (parser env query target)))))

(defn run []
  (debug "Running playground/run")

  (reset! routes/app-root "/play")
  (set! web.ui.utils/*playground?* true)
  (let [conn (utils/init-conn)]
    (app/initialize-app conn {:parser (parser-without-remote-mutations (parser/client-parser))
                              :send   (backend/send!
                                        utils/reconciler-atom
                                        {:remote (-> (remotes/post-to-url homeless/om-next-endpoint-playground)
                                                     (remotes/read-basis-t-remote-middleware conn))})})))
