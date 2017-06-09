(ns eponai.common.ui.search-bar
  (:require [om.next :as om :refer [defui]]
            [eponai.common.ui.dom :as dom]
            [taoensso.timbre :refer [debug]]
            [eponai.common.mixpanel :as mixpanel]
            [eponai.client.routes :as routes]))

(defprotocol ISearchBar
  (trigger-search! [this]))

(defui SearchBar
  ISearchBar
  (trigger-search! [this]
    (let [{:keys [input-search]} (om/get-state this)]
      (mixpanel/track "Search products" {:source        (:mixpanel-source (om/props this))
                                         :search-string input-search})
      (routes/set-url! this
                       :browse/all-items
                       nil
                       {:search input-search})))
  Object
  (render [this]
    (let [{:keys [mixpanel-source placeholder default-value classes]} (om/props this)
          {:keys [input-search]} (om/get-state this)]
      (assert (some? mixpanel-source) "missing required props :mixpanel-source to SearchBar.")
      (dom/input {:classes      classes
                  :placeholder  placeholder
                  :type         "text"
                  :value        (or input-search default-value "")
                  :onFocus      prn
                  :onChange     #(do (debug " search " (.. % -target -value))
                                     (om/update-state! this assoc :input-search (.. % -target -value)))
                  :onKeyDown    (fn [e]
                                  #?(:cljs
                                     (when (= 13 (.. e -keyCode))
                                       (trigger-search! this))))}))))

(def ->SearchBar (om/factory SearchBar))