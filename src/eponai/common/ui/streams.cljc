(ns eponai.common.ui.streams
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product-item :as pi]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.product :as product]))

(defui Streams
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/streams [:stream/name {:stream/store [:store/name {:store/photo [:photo/path]}]}]}])
  Object
  (render [this]
    (let [{:keys [query/streams proxy/navbar]} (om/props this)]
      (dom/div
        #js {:id "sulo-streams" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
          (dom/div #js {:id "sulo-items-container"}
            (apply dom/div #js {:className "row small-up-2 medium-up-3 large-up-4"}
                   (map (fn [s]
                          (common/online-channel-element s))
                        streams))))))))

(def ->Streams (om/factory Streams))
