(ns eponai.web.ui.utils
  (:require [eponai.client.ui :refer-macros [opts] :refer [map-all update-query-params!]]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [sablono.core :refer-macros [html]]
            [om.next :as om]
            [om.dom :as dom]
            [taoensso.timbre :refer-macros [debug warn]]
            [eponai.common.format :as format]
            [datascript.core :as d]
            [eponai.web.routes :as routes]))

(defonce reconciler-atom (atom nil))

;;;;;;; Om dynamic query helpers

(defprotocol IDynamicQuery
  (dynamic-query [this])
  (next-query [this next-props]))

(defprotocol IDynamicQueryChildren
  (dynamic-query-children [this next-props]))

(defprotocol IDynamicQueryParams
  ;; -> {:child Class or Component}
  (dynamic-params [this next-props]))

(defn update-dynamic-query [parser state c]
  {:pre  [(or (om/component? c) (fn? c))]
   :post [(or (nil? %) (vector? %))]}
  (cond
    (satisfies? IDynamicQuery c)
    (let [query (dynamic-query c)
          next-props (parser {:state state} query)
          next-query (next-query c next-props)
          next-params (when (satisfies? IDynamicQueryParams c)
                        (let [params (dynamic-params c next-props)
                              bound-params (reduce-kv (fn [p k x] (assoc p k (update-dynamic-query parser state x))) {} params)]
                          bound-params))]
      (debug "Setting query on component: " (pr-str c) " query: " next-query " params: " next-params)
      (when (= next-query (om/get-query c))
        (warn "Setting the same query"))
      (when (om/component? c)
        (om/set-query! c {:query next-query :params next-params} []))
      next-query)

    (satisfies? om/IQuery c)
    (om/get-query c)
    :else
    (do (debug "Who is this component: " (pr-str c)))))

(defn update-dynamic-queries! [reconciler]
  {:pre [(om/reconciler? reconciler)]}
  (let [parser (-> reconciler :config :parser)
        state (om/app-state reconciler)]
    (update-dynamic-query parser state (om/app-root reconciler))))
;; Is the problem that we're setting a query for om/app-root? hmm..

(defn component->ref [c]
  (keyword (pr-str c)))

(defn component->query-key [c]
  (let [k (component->ref c)]
    (keyword "proxy" (str (namespace k) "." (name k)))))

;;;;;;; UI component helpers

(defn loader []
  (html
    [:div.loader-circle-black
     (opts {:style {:top      "50%"
                    :left     "50%"
                    :position :absolute
                    :z-index  1050}})]))

(defn click-outside-target [on-click]
  (html
    [:div.click-outside-target
     (opts {:on-click #(when (= "click-outside-target" (.-className (.-target %)))
                        (on-click))})]))

(defn modal [{:keys [content on-close size]}]
  (let [click-outside-target-id (name :click-outside-target)]
    (html
      [:div.reveal-overlay
       (opts {:id       click-outside-target-id
              :style    {:z-index  2050
                         :display  "block"}
              :on-click #(when (= click-outside-target-id (.-id (.-target %)))
                          (on-close))})
       [:div
        (opts {:class (if size (str size " reveal") "reveal")
               :style (cond-> {:display  "block"
                               :position :relative}
                              (= size "large")
                              (assoc :margin-top 0))})
        [:a.close-button
         {:on-click on-close}
         "x"]
        content]])))

(defn upgrade-button [& [options]]
  (html
    [:a.upgrade-button
     (opts (merge {:href (routes/key->route :route/subscribe)}
                  options))
     [:strong "Upgrade"]]))

(defn tag [{tag-name :tag/name} {:keys [on-delete
                                        on-click]}]
  (dom/div #js {:className "label secondary tag"
                :style     #js {:display "inline-block"}
                :key tag-name}
    (dom/a #js {:className "button"
                :onClick on-click}
           (dom/small nil tag-name))
    (when on-delete
      (dom/a #js {:className "button"
                  :style #js {:padding "0 0.2em"}
                  :onClick on-delete}
             (dom/small nil (dom/strong nil "x"))))))

(defn add-tag [tags tag]
  (if-not (some #(= (:tag/name %) (:tag/name tag)) tags)
    (if (nil? tags)
      [tag]
      (conj tags tag))
    tags))

(defn delete-tag [tags tag]
  (into [] (remove #(= (:tag/name %) (:tag/name tag))) tags))

(defn- on-enter-down [e f]
  (when (and (= 13 (.-keyCode e))
             (seq (.. e -target -value)))
    (.preventDefault e)
    (f (.. e -target -value))))

(defn tag-input [{:keys [input-tag
                         selected-tags
                         on-change
                         on-add-tag
                         on-delete-tag
                         placeholder
                         no-render-tags?
                         input-only?]}]
  (html
    [:div

     (if input-only?
       [:input
        {:type        "text"
         :value       (or (:tag/name input-tag) "")
         :on-change   #(on-change {:tag/name (.-value (.-target %))})
         :on-key-down (fn [e]
                        (on-enter-down e #(on-add-tag {:tag/name %})))
         :placeholder (or placeholder "Filter tags...")}]

       [:div.input-group
        (opts {:style {:margin-bottom 0}})
        [:input.input-group-field
         {:type        "text"
          :value       (or (:tag/name input-tag) "")
          :on-change   #(on-change {:tag/name (.-value (.-target %))})
          :on-key-down (fn [e]
                         (on-enter-down e #(on-add-tag {:tag/name %})))
          :placeholder (or placeholder "Filter tags...")}]
        [:span.input-group-label
         [:i.fa.fa-tag]]])

     (when-not no-render-tags?
       [:div
        (map-all
          selected-tags
          (fn [t]
            (tag t
                 {:on-delete #(on-delete-tag t)})))])]))

(defn on-change-in
  "Function that updates state in component c with assoc-in for the specified keys ks.
  Calls f on the input value and updates state with that, (or identity if not provided).
  Function f takes one argument that's the value of the input."
  ([c ks]
    (on-change-in c ks identity))
  ([c ks f]
   {:pre [(om/component? c) (vector? ks)]}
   (fn [e]
     (om/update-state! c assoc-in ks (f (.-value (.-target e)))))))

(defn on-change [c k]
  {:pre [(keyword? k)]}
  (on-change-in c [k]))

;;############## Drag-drop transactions #############

(defn on-drag-transaction-start [_ tx-uuid event]
  (.. event -dataTransfer (setData "uuid-str" (str tx-uuid))))

(defn on-drag-transaction-over [component project-uuid event]
  (let [{:keys [drop-target]} (om/get-state component)]
    (.preventDefault event)
    (when-not (= drop-target project-uuid)
      (om/update-state! component assoc :drop-target project-uuid))))

(defn on-drag-transaction-leave [component _]
  (om/update-state! component dissoc :drop-target))

(defn on-drop-transaction [component project-uuid event]
  (.preventDefault event)
  (let [t-uuid (.. event -dataTransfer (getData "uuid-str"))]
    (om/transact! component `[(transaction/edit ~{:transaction/uuid   (format/str->uuid t-uuid)
                                                  :transaction/project {:project/uuid (str project-uuid)}
                                                  :mutation-uuid      (d/squuid)})])
    (om/update-state! component dissoc :drop-target)))