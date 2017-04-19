(ns eponai.common.ui.help
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.encoding-guide :as encoding]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.navbar :as nav]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]))

(defui Help
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     :query/current-route])
  Object
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [current-route]} (om/props this)]
      (common/page-container
        {:navbar navbar :id "sulo-help"}
        (grid/row-column
          nil
          (dom/div
            (css/add-class :app-title)
            (dom/a
              nil
              (dom/span nil "SULO Live help")))
          (condp = (:route current-route)
            :help/encoding (encoding/->EncodingGuide)))))))

(def ->Help (om/factory Help))