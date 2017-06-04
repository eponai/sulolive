(ns eponai.common.ui.loading-bar
  (:require [om.next :as om]))

(defprotocol ILoadingBar
  (start-loading! [this reconciler])
  (stop-loading! [this reconciler]))

(defn loading-bar []
  (reify ILoadingBar
    (start-loading! [_ reconciler]
      ;(om/transact! reconciler `[(loading-bar/show) {:query/loading-bar [:ui.singleton.loading-bar/show?]}])
      )
    (stop-loading! [_ reconciler]
      ;(om/transact! reconciler `[(loading-bar/hide) {:query/loading-bar [:ui.singleton.loading-bar/show?]}])
      )))
