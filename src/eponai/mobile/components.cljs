(ns eponai.mobile.components
  (:require-macros [eponai.mobile.components :refer [create-component-functions]])
  (:require [eponai.mobile.components.listview-datasource :as ds]
            [goog.object :as goog.object]
            [om.next]
            [om.util]))

(defn list-view-data-source
  "Returns a React.ListView.DataSource(ish) which works
  with clojures datastructures. Pass a maps if you want
  sections or arrays if you just want rows. The keys of
  the maps will be the section names.

  Usage in defui, where :data is your key to your data:
  (initLocalState [this]
    {:data-source (list-view-data-source nil
                                         (:data (om/props this)))})
  (componentWillReceiveProps [this next-props]
    (om/update-state! this assoc :data-source
                      (list-view-data-source (:data (om/props this))
                                             (:data next-props))))
   (render [this]
     ...
     (list-view {:data-source (:data-source (om/get-state this))
                 ...
                 }))"
  [prev-data next-data]
  (if (map? next-data)
    (ds/data-source-with-sections prev-data next-data)
    (ds/data-source-sectionless prev-data next-data)))

(defn create-element [element options & children]
  (apply js/React.createElement
         element
         (when options (clj->js options))
         children))

(defn element-props [element]
  (goog.object/get element "eponai$props"))

(create-component-functions)