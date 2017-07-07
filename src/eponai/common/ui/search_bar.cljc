(ns eponai.common.ui.search-bar
  (:require [om.next :as om :refer [defui]]
            [eponai.common.parser :as parser]
            [eponai.common.search :as common.search]
            [eponai.common.database :as db]
            [eponai.common.ui.dom :as dom]
            [eponai.common.ui.elements.css :as css]
            [eponai.common.ui.elements.menu :as menu]
            [taoensso.timbre :refer [debug]]
            [eponai.common.mixpanel :as mixpanel]
            [eponai.client.routes :as routes]
            [clojure.string :as str]
            [eponai.common.ui.elements.grid :as grid]
            [eponai.client.auth :as client.auth]
            [eponai.common.browse :as browse]))

(defprotocol ISearchBar
  (trigger-search! [this]))

(defui SearchBar
  ISearchBar
  (trigger-search! [this]
    (let [route-map (routes/current-route this)]
      (if-let [input-search (not-empty (some-> (:input-search (om/get-state this))
                                               (str/trim)))]
        (let [route-map (-> route-map
                            (assoc :route :browse/all-items)
                            (update :route-params select-keys [:locality])
                            (assoc-in [:query-params :search] input-search)
                            ;; Add locality if it's not already in there
                            (cond-> (nil? (get-in route-map [:route-params :locality]))
                                    (assoc-in [:route-params :locality]
                                              (:sulo-locality/path (client.auth/current-locality this)))))
              ;; Set the order to the default order for search, if there wasn't a search
              ;; there already
              route-map (cond-> route-map
                                (nil? (get-in route-map [:query-params :search]))
                                (assoc-in [:query-params :order]
                                          (browse/default-order (:query-params route-map))))]
          (routes/set-url-map! this route-map)
          (om/update-state! this assoc :input-search input-search))
        (do (routes/set-url-map! this {:route        :browse/all-items
                                       :route-params {:locality (get-in route-map [:route-params :locality])}})
            (om/update-state! this assoc :input-search "")))))
  Object
  (render [this]
    (let [{:keys [mixpanel-source placeholder default-value classes locations]} (om/props this)
          {:keys [input-search]} (om/get-state this)
          search-db (some-> (db/to-db this)
                            (db/entity [:ui/singleton :ui.singleton/product-search])
                            :ui.singleton.product-search/db)
          search-matches (when (and (string? input-search) (seq (str/trim input-search)))
                           (some-> search-db
                                   (common.search/match-string input-search)))]
      (dom/div (css/add-class :sulo-search-bar {:id "sulo-search-bar"})
               (dom/input {:classes     classes
                           :placeholder placeholder
                           :type        "text"
                           :value       (or input-search default-value "")
                           :onFocus     #(when-not (::has-requested-search-suggestions (om/get-state this))
                                           (om/update-state! this assoc ::has-requested-search-suggestions true)
                                           (binding [parser/*parser-allow-local-read* false]
                                             (om/transact! (om/get-reconciler this) [:query/product-search])))
                           :onChange    #(let [search-val (.. % -target -value)]
                                           (om/update-state! this assoc :input-search search-val))
                           :onKeyDown   (fn [e]
                                          #?(:cljs
                                             (when (= 13 (.. e -keyCode))
                                               (trigger-search! this))))})
               (dom/div
                 (cond->> (css/add-class :dropdown-pane)
                          false ;; (seq search-matches)
                          (css/add-class :is-open))
                 (menu/vertical
                   nil
                   (menu/item
                     nil
                     (dom/label nil (dom/small nil "Products"))
                     (menu/vertical
                       nil
                       (into []
                             (comp
                               (take-while seq)
                               (mapcat (fn [matches]
                                         (sort-by (comp count #(nth % 1))
                                                  #(compare %2 %1)
                                                  matches)))
                               (take 4)
                               (map (fn [[word refs]]
                                      (menu/item-link
                                        {:href    (routes/url :browse/all-items {:locality (:sulo-locality/path locations)} {:search word})
                                         :onClick #(mixpanel/track "Search products"
                                                                   {:source        mixpanel-source
                                                                    :search-string word})}
                                        (grid/row
                                          (css/add-class :suggestion-row)
                                          (grid/column (grid/column-size {:small 10})
                                                       (dom/span nil word))
                                          (grid/column (grid/column-size {:small 2})
                                                       (dom/span nil (str (count refs)))))))))
                             (iterate #(common.search/match-next-word search-db %) search-matches))))))))))

(def ->SearchBar (om/factory SearchBar {:validator (fn [props]
                                                     (every? #(contains? props %)
                                                             [:mixpanel-source]))}))