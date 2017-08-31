(ns eponai.web.header
  (:require [medley.core :as medley]
            [taoensso.timbre :refer [debug error]]
            [om.dom :as dom]
            [eponai.common.database :as db]
            [clojure.data :as data])
  #?(:clj
     (:import [om.dom Element])))

;; To create your own tags, overwrite these three multi methods
;; They all dispatch on the :tag key.

(defmulti unique-attributes
          "Return all keywords that make the tag unique. For example, there should only be one
          :title tag in the header, so :title's implementation is [:tag].
          :meta is unique by a combination of attributes and it returns [:tag :id :name :property]."
          (fn [{:keys [tag]}] tag))

(defmulti mutate-dom-node!
          "Mutate a JavaScript HTMLElement given a tag-map."
          (fn [{:keys [tag]} node] tag))

(defmulti create-om-element
          "Return a om.dom element that represents the tag-map."
          (fn [{:keys [tag]}] tag))

(declare set-node-inner-text set-node-attributes!)

;; <title>
(defmethod unique-attributes :title [_] [:tag])

(defmethod mutate-dom-node! :title
  [{:keys [content] :as head-meta} node]
  (set-node-inner-text node content)
  (set-node-attributes! node (dissoc head-meta :content)))

(defmethod create-om-element :title
  [{:keys [tag content] :as head-meta}]
  (dom/title (dissoc head-meta :tag :content) content))

;; <meta>
(defmethod unique-attributes :meta [_] [:id :name :property])

(defmethod mutate-dom-node! :meta
  [head-meta node]
  (set-node-attributes! node head-meta))

(defmethod create-om-element :meta
  [{:keys [tag content] :as head-meta}]
  (dom/meta (dissoc head-meta :tag)))


;; Implementation

(defprotocol IHeadTag
  (tag-map [this] "Returns a map with :tag :id :name :property and :content. Also has the original object in :node."))

#?(:cljs
   (extend-type js/HTMLElement
     IHeadTag
     (tag-map [this]
       (let [inner-text (.-innerText this)
             tag (-> (.-nodeName this) (clojure.string/lower-case) (keyword))]
         (-> {:node this
              :tag  tag}
             (into (comp (map (juxt identity #(.getAttribute this (name %))))
                         (filter (comp some? second)))
                   [:id :name :property :content])
             (cond-> (seq inner-text)
                     (assoc :inner-text inner-text)))))))
#?(:clj
   (extend-type Element
     IHeadTag
     (tag-map [this]
       (let [inner-text (-> this :children first :s)]
         (-> (:attrs this)
             (merge {:node this
                     :tag  (keyword (:tag this))})
             (cond-> (seq inner-text)
                     (assoc :inner-text inner-text)))))))


(defn match-head-meta-to-dom-nodes-xf [nodes]
  (let [nodes-by-tags-by-keys
        (->> nodes
             (group-by :tag)
             (into {}
                   (map (fn [[tag nodes]]
                          [tag (into {:tag {tag nodes}}
                                     (map (juxt identity #(group-by % nodes)))
                                     ;; Index by id, name and property
                                     [:id :name :property])]))))]
    (map (fn [head-meta]
           (let [nodes-by-keys (get nodes-by-tags-by-keys (:tag head-meta))
                 matches-by-key (->> (unique-attributes head-meta)
                                     (into []
                                           (comp (map (fn [k]
                                                        (when-some [v (get head-meta k)]
                                                          (let [nodes-by-k-val (get nodes-by-keys k)]
                                                            (get nodes-by-k-val v)))))
                                                 (filter seq)
                                                 (map set)))
                                     not-empty)
                 matched (when matches-by-key
                           (apply clojure.set/intersection matches-by-key))]
             (assert (or (empty? matched)
                         (= 1 (count matched)))
                     (str "Matched was more than 1, was: " matched
                          " head-meta: " head-meta))
             [head-meta (first matched)])))))

(defn split-by-matched [matches]
  (let [{with-nodes true without-nodes false} (group-by (comp some? second) matches)]
    {:with-match    with-nodes
     :without-match without-nodes}))

(defn match-head-meta-to-dom-nodes
  "Matches head node data (maps with {:tag :meta :name \"foo\"}) to dom objects.
  Returns a seq of pairs with [head-meta dom-node?]. When dom-node is nil, that means
  a new dom-node should be created.

  The order of head-data matters. The items in head-data is distinct in the order they
  appear."
  [head-data dom-nodes]
  {:pre [(every? (every-pred map? (comp some? :tag)) head-data)
         (every? #(satisfies? IHeadTag %) dom-nodes)]}
  (let [dom-nodes (map tag-map dom-nodes)
        matches (->> head-data
                     (into [] (comp (match-head-meta-to-dom-nodes-xf dom-nodes)
                                    (medley/distinct-by (fn [[head-meta node]]
                                                          ;; If there's no matching node, distinct by the head-meta
                                                          ;; to avoid creating duplicates.
                                                          (or node head-meta))))))
        {:keys [with-match without-match]} (split-by-matched matches)
        without-match (when (seq without-match)
                        ;; Creates fake nodes to de-dupe the head-meta without any nodes yet.
                        (let [fake-nodes (->> without-match
                                              (map (fn [[head-meta]]
                                                     (reify IHeadTag
                                                       (tag-map [this]
                                                         (select-keys head-meta [:tag :id :name :property])))))
                                              (medley/distinct-by (fn [head-tag]
                                                                    (let [m (tag-map head-tag)]
                                                                      (select-keys m (unique-attributes m))))))]
                          ;; Match again to remove the duplicates.
                          (->> (match-head-meta-to-dom-nodes (map first without-match) fake-nodes)
                               ;; Remove the fake node from the matches.
                               (map #(assoc % 1 nil)))))]
    (concat with-match without-match)))


;; CLJS
(defprotocol IUpdateHeader
  (update-header! [this route reconciler]))

#?(:cljs
   (defn dom-head []
     (first (array-seq (.getElementsByTagName js/document "head")))))

(defn- set-node-attributes!
  "Takes an HTMLElement (node), a map and keys, and sets all keys as attributes
  on the node with values from the map."
  [node m]
  (->> (dissoc m :tag :node)
       (run! (fn [[k v]]
               (.setAttribute node (name k) (str v))))))

(defn- set-node-inner-text [node text]
  (set! (.-innerText node) (str text)))

#?(:cljs
   (defn mutate-dom-nodes [head-to-node-matches]
     (let [head (dom-head)]
       (->> head-to-node-matches
            (run! (fn [[head-meta node]]
                    (if (some? node)
                      (let [node (:node node)]
                        (mutate-dom-node! head-meta node))
                      (let [node (.createElement js/document (name (:tag head-meta)))]
                        (mutate-dom-node! head-meta node)
                        (.appendChild head node)))))))))

#?(:cljs
   (defn header
     "header-meta-fn is a 1-arity function returning a sequence of <head> tag children."
     [header-meta-fn]
     (let [cache (atom {})]
       (reify IUpdateHeader
         (update-header! [this route-map reconciler]
           (let [head-meta (into []
                                 (filter some?)
                                 (header-meta-fn {:route-map  route-map
                                                  :db         (db/to-db reconciler)
                                                  :reconciler reconciler}))]
             ;; Avoid updating the same header info again.
             (if (= head-meta (:head-meta @cache))
               (debug "Head-meta was equal to last time, won't run the update.")
               (do
                 (swap! cache assoc :head-meta head-meta)
                 (let [dom-nodes (->> (dom-head) (.-children) (array-seq))
                       matches (match-head-meta-to-dom-nodes head-meta dom-nodes)]
                   (mutate-dom-nodes matches))))))))))

;; CLJ

#?(:clj
   (defn update-dom-head [dom-head head-meta]
     (let [matches (match-head-meta-to-dom-nodes head-meta
                                                 (:children dom-head))
           {:keys [with-match without-match]} (split-by-matched matches)]
       (update dom-head :children
               (fn [children]
                 (let [matches-by-node (into {}
                                             (map (juxt (comp :node second) identity))
                                             with-match)]
                   (-> []
                       (into (map (fn update-node [child]
                                    (if-let [match (get matches-by-node child)]
                                      (let [head-meta (first match)]
                                        (create-om-element head-meta))
                                      child)))
                             children)
                       (into (map (comp create-om-element first)) without-match))))))))

;; Convenience functions

(defn title-tag
  "Creates a title tag map. Returns nil if title is nil."
  [title]
  (when (some? title)
    {:tag     :title
     :content title}))

(defn meta-tag
  "Helper function for creating the meta tags.
   Called with (meta-tag :id \"foo\" :content 123)

   Must contain either id, name or property. Can be all of them."
  [& {:as kvs}]
  {:post [(some #{:id :name :property} (keys %))]}
  (into {:tag :meta}
        (filter (comp some? val))
        kvs))

(defn description-tag
  "Returns a description meta tag map. Returns nil if description is nil."
  [description & more]
  (when (some? description)
    (apply meta-tag
           :name "description"
           :content description
           more)))
