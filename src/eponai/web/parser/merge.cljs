(ns eponai.web.parser.merge
  (:require [datascript.core :as d]
            [eponai.client.parser.merge :as m]
            [taoensso.timbre :refer-macros [debug]]))

(defmulti web-merge (fn [_ k _] k))
(defmethod web-merge :default
  [_ _ _]
  nil)

(defmethod web-merge 'playground/subscribe
  [db k {:keys [status]}]
  (m/transact db [{:ui/component :ui.component/sidebar
                   :ui.component.sidebar/newsletter-subscribe-status
                                 (if (<= 200 status 299)
                                   :ui.component.sidebar.newsletter-subscribe-status/success
                                   :ui.component.sidebar.newsletter-subscribe-status/failed)}]))
