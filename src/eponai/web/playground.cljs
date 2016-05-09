(ns eponai.web.playground
  (:require [eponai.web.app :as app]
            [eponai.web.ui.utils :as utils]
            [eponai.common.parser :as parser]
            [taoensso.timbre :refer-macros [debug]]
            [eponai.client.backend :as backend]
            [eponai.web.homeless :as homeless]))

(defn parser-without-remote-mutations [parser]
  (let [parse-without-mutations (parser/parse-without-mutations parser)]
    (fn p
      [env query & [target]]
      (if (some? target)
        (parse-without-mutations env query target)
        (parser env query target)))))

(defn run []
  (let [conn (utils/init-conn)]
    (app/initialize-app conn {:parser (parser-without-remote-mutations (parser/parser))
                              :send   (backend/send! {:remote (-> (backend/post-to-url homeless/om-next-endpoint-playground)
                                                                  (utils/read-basis-t-remote-middleware conn))})})))
