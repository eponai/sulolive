(ns eponai.common.ui.search-bar
  (:require [om.next :as om :refer [defui]]
            [eponai.common.parser :as parser]
            [eponai.common.search :as common.search]
            [eponai.common.database :as db]
            [eponai.common.ui.dom :as dom]
            [eponai.common.ui.elements.css :as css]
            [eponai.common.ui.elements.menu :as menu]
            [eponai.common.ui.components.select :as select]
            [taoensso.timbre :refer [debug]]
            [eponai.common.mixpanel :as mixpanel]
            [eponai.client.routes :as routes]
            [clojure.string :as str]
    #?(:cljs [goog.events.KeyCodes :as keycodes])
            [eponai.common.ui.elements.grid :as grid]))

(defprotocol ISearchBar
  (trigger-search! [this]))

(defn suggestions [search-db search-matches]
  (into []
        (comp
          (take-while seq)
          (mapcat (fn [matches]
                    (sort-by (comp count #(nth % 1))
                             #(compare %2 %1)
                             matches)))
          (take 4)
          (map (fn [[word refs]]
                 {:value word
                  :label (grid/row
                           (css/add-class :suggestion-row)
                           (grid/column (grid/column-size {:small 10})
                                        (dom/span nil word))
                           (grid/column (grid/column-size {:small 2})
                                        (dom/span nil (str (count refs)))))})))
        (iterate #(common.search/match-next-word search-db %) search-matches)))

(defn- do-trigger-search [component search]
  (mixpanel/track "Search products"
                  {:source        (:mixpanel-source (om/props component))
                   :search-string search})
  (routes/set-url! component
                   :browse/all-items
                   nil
                   {:search search}))

(defui SearchBar
  ISearchBar
  (trigger-search! [this]
    (let [{:keys [input-search]} (om/get-state this)]
      (do-trigger-search this input-search)))
  Object
  (render [this]
    (let [{:keys [mixpanel-source placeholder default-value classes]} (om/props this)
          {:keys  [input-search]
           ::keys [has-requested-search-suggestions]} (om/get-state this)
          input-id (str "search_bar_input_id_" (hash mixpanel-source))
          search-db (some-> (db/to-db this)
                            (db/entity [:ui/singleton :ui.singleton/product-search])
                            :ui.singleton.product-search/db)
          search-matches (when (and (string? input-search) (seq (str/trim input-search)))
                           (some-> search-db
                                   (common.search/match-string input-search)))
          sugs (suggestions search-db search-matches)]
      (debug "input-id: " input-id)
      (dom/div (css/add-class :sulo-search-bar)
               ;; TODO: propagate classes?
               (select/->SelectOne
                 (om/computed
                   {:classes        classes
                    :placeholder    placeholder
                    :value          (zipmap [:label :value] (repeat (or input-search default-value "")))
                    :options        sugs
                    :noResultsText  (if (empty? input-search)
                                      "Start typing for suggestions..."
                                      (dom/div
                                        nil
                                        (dom/p nil (str "Sorry, we have suggestions for: " input-search))
                                        (dom/span nil "You can still search and find it if you're lucky! 🎲")
                                        (dom/br nil)
                                        (dom/span nil "We're working on making suggestions better 👩‍💻")))
                    ;; Override filters. We can create our own options, thank you very much!
                    :filterOption   (constantly true)
                    :onInputKeyDown #?(:clj  identity
                                       :cljs (fn [event]
                                               (condp = (.-keyCode event)
                                                 keycodes/ENTER (when (and (seq (str/trim input-search))
                                                                           (empty? sugs))
                                                                  (trigger-search! this)
                                                                  (.preventDefault event))
                                                 keycodes/RIGHT (debug "Target: " (.-target event)
                                                                       " val: " (.-value (.-target event)))
                                                 nil)))
                    :onInputChange  (fn [input]
                                      (om/update-state! this assoc :input-search input)
                                      input)
                    :onFocus        #(when-not has-requested-search-suggestions
                                       (om/update-state! this assoc ::has-requested-search-suggestions true)
                                       (binding [parser/*parser-allow-local-read* false]
                                         (om/transact! (om/get-reconciler this) [:query/product-search])))
                    ;; TODO: isLoading isn't really working.
                    :id             input-id
                    :isLoading      #?(:clj  false
                                       :cljs (let [el (.getElementById js/document input-id)
                                                   ae (.-activeElement js/document)]
                                               (debug "el: " el " ae: " ae)
                                               (and (nil? search-db)
                                                    (= el ae))))}
                   {:on-change (fn [{:keys [value]}]
                                 (do-trigger-search this value))}))))))

(def ->SearchBar (om/factory SearchBar {:validator (fn [props]
                                                     (every? #(contains? props %)
                                                             [:mixpanel-source]))}))