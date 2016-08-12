(ns eponai.web.playground
  (:require [eponai.common.parser :as parser]
            [eponai.client.backend :as backend]
            [eponai.web.app :as app]
            [eponai.web.routes :as routes]
            [eponai.web.ui.utils :as utils]
            [eponai.web.homeless :as homeless]
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
  (set! utils/*playground?* true)
  (let [conn (utils/init-conn)]
    (app/initialize-app conn {:parser (parser-without-remote-mutations (parser/parser))
                              :send   (backend/send!
                                        utils/reconciler-atom
                                        {:remote (-> (backend/post-to-url homeless/om-next-endpoint-playground)
                                                                  (utils/read-basis-t-remote-middleware conn))})})))
