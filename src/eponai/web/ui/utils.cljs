(ns eponai.web.ui.utils
  (:require [eponai.client.ui :refer-macros [opts component-implements] :refer [map-all update-query-params!]]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [sablono.core :refer-macros [html]]
            [om.next :as om]
            [om.dom :as dom]
            [cljsjs.react.dom]
            [goog.object]
            [goog.log :as glog]
            [taoensso.timbre :refer-macros [debug warn error]]
            [eponai.common.format :as format]
            [eponai.common.datascript :as common.datascript]
            [datascript.core :as d]
            [eponai.web.routes :as routes]
            [clojure.string :as string]
            [clojure.data :as diff]))

(def ^:dynamic *playground?* false)

(defonce reconciler-atom (atom nil))

;; ---- Cached ui->props ---------------

(defn traverse-query-path
  "Takes a query and a path. Returns subqueries matching the path.

  Will return a single subquery for queries without unions.
  A path may match any of the union's branches/values."
  [query path]
  (letfn [(step-into-query [query part]
            (cond
              (= part ::root)
              [query]

              (number? part)
              [query]

              (keyword? query)
              nil

              (map? query)
              (when (contains? query part)
                (let [query' (get query part)]
                  (if (map? query')
                    ;; Union. Could be any of the values in an union.
                    ;; Hack: Take the first matching value.
                    ;; TODO: Try to match all values, making traverse-query
                    ;;       return multiple values.
                    (vals query')
                    ;; Anything else, just traverse as normal.
                    [query'])))

              (sequential? query)
              (mapcat #(step-into-query % part) query)

              :else
              (do (debug "not traversing query: " query " path-part: " part)
                  nil)))]
    (reduce (fn [q p] (into [] (comp (mapcat #(step-into-query % p))
                                     (filter some?))
                            q))
            [query]
            path)))

(defn path->paths [path]
  (->> path
       (iterate rest)
       (take-while seq)
       (map vec)))

(defn- find-cached-props
  "Given a cache with map of path->(query, props), a component and a component's full query,
  check the cache if the component's full query has already been parsed (read).

  Returns nil or props for a component."
  [cache c-path c-query]
  (let [exact-subquery (traverse-query-path c-query c-path)
        _ (when-not (= 1 (count exact-subquery))
            (warn "Exact subquery was not an only match! Was: " exact-subquery))
        exact-subquery (first exact-subquery)
        find-props (fn [{:keys [::query ::props]}]
                     (let [subqueries (traverse-query-path query c-path)]
                       (when (some #(= % exact-subquery) subqueries)
                         (let [c-props (get-in props c-path)]
                           (when (some? c-props)
                             (debug "found cached props for c-path: " c-path))
                           c-props))))
        ret (->> (butlast c-path)
                 (path->paths)
                 (cons [::root])
                 (map #(get-in cache %))
                 (filter some?)
                 (some find-props))]
    ret))

(defn cached-ui->props
  "Takes an atom to store queries->props, app-state, component, full query of
  the component and a thunk for calling the parser for cache misses.
  Uses the query and the components path to find queries already parsed.

  Example:
  Let's say we've got components A and B.
  B is a subquery of A, i.e. query A: [... (om/get-query B) ...]
  When we parse A, we'll get the props of both A and B.
  We can get B's props by using (om/path B) in props of A.
  This is what we're doing with (find-cached-props ...)."
  [cache state component query parser-thunk]
  {:pre [(implements? IDeref cache)
         (implements? IDeref state)
         (om/component? component)
         (vector? query)
         (fn? parser-thunk)]}
  (let [path (om/path component)
        db (d/db state)
        cache-db (::db @cache)]
    (when-not (identical? db cache-db)
      ;; db's are not identical. Reset the cache.
      (reset! cache {::db db}))
    (let [props (or (find-cached-props @cache path query)
                    (parser-thunk))]
      (swap! cache update-in
             (or (seq path) [::root])
             merge
             {::query query
              ::props props})
      props)))

(defn cached-ui->props-fn
  "Takes a component and returns its props, just like om.next/default-ui->props.
  This function will also cache a component's props based on it's path and query
  to provide fast lookups for subcomponents, skipping a lot of reads."
  [parser]
  (let [cache (atom {})]
    (fn [env c]
      {:pre [(map? env) (om/component? c)]}
      (let [fq (om/full-query c)]
        (when-not (nil? fq)
          (let [s (system-time)
                ui (cached-ui->props cache (:state env) c fq #(-> (parser env fq)
                                                                  (get-in (om/path c))))
                e (system-time)]
            (when-let [l (:logger env)]
              (let [dt (- e s)]
                (when (< 16 dt)
                  (glog/warning l (str (pr-str c) " query took " dt " msecs")))))
            ui))))))

(defn debug-ui->props-fn
  "Debug if our function is not acting as om.next's default ui->props function."
  [parser]
  (let [eponai-fn (cached-ui->props-fn parser)]
    (fn [env c]
      (let [om-ui (om/default-ui->props env c)
            eponai-ui (eponai-fn env c)]
        (when (not= om-ui eponai-ui)
          (warn "Om and eponai UI differ for component: " (pr-str c) " diff: " (diff/diff om-ui eponai-ui)))
        eponai-ui))))

;; ---- END Cached ui->props ------------

;;;;;;; Helpers for remote communcation

;; TODO: Move this function somewhere else?
(defn read-basis-t-remote-middleware
  "Given a remote-fn (that describes what, where and how to send a request to a server),
  add basis-t for each key to the request. basis-t represents at which basis-t we last
  read a key from the remote db."
  [remote-fn conn]
  (fn [query]
    (let [ret (remote-fn query)
          db (d/db conn)]
      (assoc-in ret [:opts :transit-params :eponai.common.parser/read-basis-t]
                (some->> (d/q '{:find [?e .] :where [[?e :db/ident :eponai.common.parser/read-basis-t]]}
                              db)
                         (d/entity db)
                         (d/touch)
                         (into {}))))))

;;;;;;; Om query helpers

(defn subq-or-static-q [x ref-key class]
  (or (when (om/component? x)
        (let [ref-component (om/react-ref x (cond-> ref-key (keyword? ref-key) str))
              type-eq (= (type ref-component) class)]
          (debug "Type eq: " type-eq " for class: " class " ref-component: " ref-component)
          (when (true? type-eq)
            (let [ret (om/subquery x ref-key class)]
              (debug "Returning subquery for class: " class " query: " ret)
              ret))))
      (om/get-query class)))

(defn query-with-component-meta [x query]
  (with-meta query
             {:component (cond
                           (om/component? x) (type x)
                           (goog/isFunction x) x)}))

(defn component->ref [c]
  (keyword (pr-str c)))

(defn component->query-key [c]
  (let [k (component->ref c)]
    (keyword "proxy" (str (namespace k) "." (name k)))))

;;;;;;; App initialization

(defonce conn-atom (atom nil))

(defn init-conn
  "Sets up the datascript state. Caches the state so we can keep our app state between
  figwheel reloads."
  []
  (if @conn-atom
    (do
      (debug "Reusing old conn. It currently has schema for attributes:" (-> @conn-atom deref :schema keys))
      @conn-atom)
    (let [ui-state [{:ui/singleton :ui.singleton/app}
                    {:ui/singleton :ui.singleton/auth}
                    {:ui/component                      :ui.component/project
                     :ui.component.project/selected-tab :dashboard}
                    {:ui/component :ui.component/widget}
                    {:ui/component :ui.component/root}
                    {:ui/component :ui.component/mutation-queue}
                    {:ui/component :ui.component/sidebar
                     :ui.component.sidebar/newsletter-subscribe-status
                                   :ui.component.sidebar.newsletter-subscribe-status/not-sent}]
          conn (d/create-conn (common.datascript/ui-schema))]
      (d/transact! conn ui-state)
      (reset! conn-atom conn))))


;;;;;;; UI component helpers

(defprotocol ISyncStateWithProps
  (props->init-state [this props] "Takes props and returns initial state."))

(defn sync-with-received-props [component new-props & [{:keys [will-sync did-sync without-logging]}]]
  {:pre [(and (om/component? component) (satisfies? ISyncStateWithProps component))]}
  (when (not= new-props (om/props component))
    (let [this-state (om/get-state component)
          next-state (props->init-state component new-props)]
      (when-not without-logging
        (debug "Reseting initial state for component: " component
               " diff between old and new props:" (diff/diff (om/props component) new-props)
               "next-state: " next-state))
      ;; Call a function to unmount stateful state.
      ;; Called with the old and the next state.
      (when will-sync
        (will-sync this-state next-state))
      (om/set-state! component next-state)
      (when did-sync
        (did-sync this-state (om/get-state component))))))

(defn ref-dom-node [component ref-name]
  {:pre [(om/component? component) (string? ref-name)]}
  (when-let [ref (om/react-ref component ref-name)]
    (js/ReactDOM.findDOMNode ref)))

(defn focus-ref
  "Calls .focus on ref's dom node. Returns true if focus was called."
  [component ref-name]
  {:pre [(om/component? component) (string? ref-name)]}
  (when-let [node (ref-dom-node component ref-name)]
    (.focus node)
    true))

(defn left-padding
  "Returns the left padding required for string s"
  [width s]
  (let [spaces (- width (.-length (str s)))
        spaces (if (neg? spaces) 0 spaces)]
    (string/join  (repeat spaces " "))))

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
                               :position :relative})})
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
  (if-let [found-tag (some #(when (= (:tag/name %) (:tag/name tag))
                             %) tags)]
    (if (= (:tag/status found-tag) :deleted)
      (replace {found-tag (dissoc found-tag :tag/status)})
      tags)
    (conj (or tags []) (assoc tag :tag/status :added))))

(defn delete-tag [tags tag]
  (if-let [found-tag (some #(when (= (:tag/name %) (:tag/name tag)) %) tags)]
    (if (= (:tag/status found-tag) :added)
      (into [] (remove #(= (:tag/name %) (:tag/name found-tag))) tags)
      (replace {found-tag (assoc found-tag :tag/status :deleted)} tags))
    (do (warn "Tag: " tag " not found in tags: " tags)
        tags)))

(defn on-enter-down [e f]
  (when (and (= 13 (.-keyCode e))
             (seq (.. e -target -value)))
    (.preventDefault e)
    (f (.. e -target -value))))

(defn tag-input [{:keys [input-tag
                         selected-tags
                         ref
                         on-change
                         on-add-tag
                         on-delete-tag
                         on-key-down
                         placeholder
                         no-render-tags?
                         input-only?]}]
  (let [input-opts {:type        "text"
                    :ref         ref
                    :value       (or (:tag/name input-tag) "")
                    :on-change   #(on-change {:tag/name (.-value (.-target %))})
                    :on-key-down (fn [e]
                                   (when on-key-down (on-key-down e))
                                   (on-enter-down e #(on-add-tag {:tag/name (clojure.string/trim %)})))
                    :placeholder (or placeholder "Filter tags...")}]
    (html
     [:div

      (if input-only?
        [:input input-opts]

        [:div.input-group
         (opts {:style {:margin-bottom 0}})
         [:input.input-group-field input-opts]
         [:span.input-group-label
          [:i.fa.fa-tag]]])

      (when-not no-render-tags?
        [:div
         (map-all
           selected-tags
           (fn [t]
             (tag t
                  {:on-delete #(on-delete-tag t)})))])])))

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

;;############# Debugging ############################

(defn shouldComponentUpdate [this next-props next-state]
  (let [next-children (. next-props -children)
        next-children (if (undefined? next-children) nil next-children)
        next-props (goog.object/get next-props "omcljs$value")
        next-props (cond-> next-props
                           (instance? om/OmProps next-props) om.next/unwrap)
        children (.. this -props -children)
        pe (not= (om.next/props this)
                 next-props)
        se (and (.. this -state)
                (not= (goog.object/get (. this -state) "omcljs$state")
                      (goog.object/get next-state "omcljs$state")))
        ce (not= children next-children)

        pdiff (diff/diff (om.next/props this) next-props)
        sdiff (diff/diff (when (.. this -state) (goog.object/get (. this -state) "omcljs$state"))
                         (goog.object/get next-state "omcljs$state"))
        cdiff (diff/diff children next-children)
        prn-diff (fn [label [in-first in-second :as diff]]
                   (when (or (some? in-first) (some? in-second))
                     (debug label " diff:" diff)))]
    (debug "this: " this
           "props-not-eq?: " pe
           " state-not-eq?:" se
           " children-not-eq?:" ce)
    (prn-diff "props diff" pdiff)
    (prn-diff "state diff" sdiff)
    (prn-diff "children diff" cdiff)
    (or pe se ce)))