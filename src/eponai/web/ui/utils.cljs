(ns eponai.web.ui.utils
  (:require [eponai.client.ui :refer-macros [opts component-implements] :refer [map-all update-query-params!]]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [sablono.core :refer-macros [html]]
            [om.next :as om]
            [om.dom :as dom]
            [cljsjs.react.dom]
            [goog.object]
            [taoensso.timbre :refer-macros [debug warn error]]
            [eponai.common.format :as format]
            [eponai.web.routes :as routes]
            [clojure.string :as string]
            [clojure.data :as diff]))

(def ^:dynamic *playground?* false)

;;;;;;; Om query helpers

(defn query-with-component-meta [x query]
  (with-meta query
             {:component (cond
                           (om/component? x) (type x)
                           (goog/isFunction x) x)}))

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
                                                  :transaction/project {:project/uuid (str project-uuid)}})])
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
