(ns eponai.common.ui.search-bar
  (:require [om.next :as om :refer [defui]]
            [eponai.common.parser :as parser]
            [eponai.common.search :as common.search]
            [eponai.common.database :as db]
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
      (dom/input {:classes     classes
                  :placeholder placeholder
                  :type        "text"
                  :value       (or input-search default-value "")
                  :onFocus     #(when-not (::has-requested-search-suggestions (om/get-state this))
                                  (om/update-state! this assoc ::has-requested-search-suggestions true)
                                  (binding [parser/*parser-allow-local-read* false]
                                    (om/transact! (om/get-reconciler this) [:query/product-search])))
                  :onChange    #(let [search-val (.. % -target -value)]
                                  (debug "search " search-val)
                                  (debug "matches: " (when (seq search-val)
                                                       (some-> (db/to-db this)
                                                               (db/entity [:ui/singleton :ui.singleton/product-search])
                                                               :ui.singleton.product-search/db
                                                               (common.search/match-string search-val))))
                                  (om/update-state! this assoc :input-search search-val))
                  :onKeyDown   (fn [e]
                                 #?(:cljs
                                    (when (= 13 (.. e -keyCode))
                                      (trigger-search! this))))}))))

(def ->SearchBar (om/factory SearchBar))