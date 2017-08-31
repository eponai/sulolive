(ns eponai.web.header
  (:require [medley.core :as medley]
            [taoensso.timbre :refer [debug error]]
            [om.dom :as dom]
            [eponai.common.database :as db])
  #?(:clj
     (:import [om.dom Element])))

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

(defn title-tag
  "Creates a title tag map. Returns nil if title is nil."
  [title & {:as args}]
  (when (some? title)
    (merge {:tag     :title
            :content title}
           args)))

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

(defmulti match-head-meta-to-nodes-by-tag (fn [tag metas nodes] tag))

(defn split-by-matched [matches]
  (let [{with-nodes true without-nodes false} (group-by (comp some? second) matches)]
    {:with-match    with-nodes
     :without-match without-nodes}))

(defn match-head-meta-to-dom-nodes
  "Matches head node data (maps with {:tag :meta :name \"foo\"}) to dom objects.
  Returns a seq of pairs with [head-meta dom-node?]. When dom-node is nil, that means
  a new dom-node should be created."
  [head-data dom-nodes]
  {:pre [(every? (every-pred map? (comp some? :tag)) head-data)
         (every? #(satisfies? IHeadTag %) dom-nodes)]}
  (let [meta-by-tag (group-by :tag head-data)
        nodes-by-tag (group-by :tag (map tag-map dom-nodes))
        matches (->> (keys meta-by-tag)
                     (into []
                           (mapcat (fn [tag]
                                     {:post [(every? vector? %)]}
                                     (match-head-meta-to-nodes-by-tag tag
                                                                      (get meta-by-tag tag)
                                                                      (get nodes-by-tag tag)))))
                     (rseq)
                     (medley/distinct-by (fn [[head-meta node]]
                                           ;; If there's no matching node, distinct by the head-meta
                                           ;; to avoid creating duplicates.
                                           (or node head-meta))))
        {:keys [with-match without-match]} (split-by-matched matches)
        without-match (when (seq without-match)
                        (->> without-match
                             ;; Creates fake nodes to de-dupe the head-meta without any nodes yet.
                             (into []
                                   (comp (map (fn [[head-meta]]
                                                (reify IHeadTag
                                                  (tag-map [this]
                                                    (select-keys head-meta [:tag :property :name :id])))))
                                         (medley/distinct-by tag-map)))
                             ;; Match again to remove the duplicates.
                             (match-head-meta-to-dom-nodes (map first without-match))
                             ;; Remove the fake node from the matches.
                             (map #(assoc % 1 nil))))]
    (concat with-match
            without-match)))

(defmethod match-head-meta-to-nodes-by-tag :title
  [_ metas nodes]
  ;; Return the last head-meta with the first node.
  ;; There should only be one title
  (when (seq nodes)
    (assert (= 1 (count nodes))
            (str "There were more than 1 title tag in <head>: " nodes)))
  [[(last metas) (first nodes)]])

(defmethod match-head-meta-to-nodes-by-tag :meta
  [_ metas nodes]
  (let [nodes-by-keys (into {} (map (juxt identity #(group-by % nodes))) [:id :name :property])
        find-matching-node (fn [nodes-by-keys head-meta]
                             (let [matched (->> [:id :name :property]
                                                (map (fn [k]
                                                       (when-some [v (get head-meta k)]
                                                         (-> (get nodes-by-keys k)
                                                             (get v)))))
                                                (filter seq)
                                                (map set)
                                                (apply clojure.set/union))]
                               (assert (or (empty? matched)
                                           (= 1 (count matched)))
                                       (str "Matched was more than 1, was: " matched
                                            " head-meta: " head-meta))
                               [head-meta (first matched)]))]
    (into []
          (map #(find-matching-node nodes-by-keys %))
          metas)))

;; CLJS
(defprotocol IUpdateHeader
  (update-header! [this route reconciler]))

#?(:cljs
   (defn dom-head []
     (first (array-seq (.getElementsByTagName js/document "head")))))

(defn- set-node-attributes!
  "Takes an HTMLElement (node), a map and keys, and sets all keys as attributes
  on the node with values from the map."
  [node m attrs]
  (debug "Will set attrs: " attrs " for head-meta: " m " selected-keys: " (select-keys m attrs))
  (->> (select-keys m attrs)
       (map (fn [[k v]] [(name k) (str v)]))
       (run! (fn [[k v]]
               ;; Don't mutate DOM if attribute is already equal.
               (when-not (= v (.getAttribute node k))
                 (debug "Setting meta attribute: " [k v])
                 (.setAttribute node k v))))))

(defn- set-node-inner-text [node text]
  (set! (.-innerText node) (str text)))

#?(:cljs
   (defn mutate-dom-node! [{:keys [tag content] :as head-meta} node]
     (condp = tag
       :title (do (set-node-inner-text node content)
                  (set-node-attributes! node
                                        head-meta
                                        [:property :name :id]))
       :meta (set-node-attributes! node
                                   head-meta
                                   [:id :name :property :content]))))

#?(:cljs
   (defn mutate-dom-nodes [head-to-node-matches]
     (let [head (dom-head)]
       (->> head-to-node-matches
            (run! (fn [[head-meta node]]
                    (if (some? node)
                      (mutate-dom-node! head-meta (:node node))
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

(defn create-node [{:keys [tag content] :as head-meta}]
  (condp = tag
    :title (dom/title (dissoc head-meta :tag :content) content)
    :meta (dom/meta (dissoc head-meta :tag))
    (throw (ex-info "No matching tag?: " {:head-meta head-meta}))))

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
                                        (create-node head-meta))
                                      child)))
                             children)
                       (into (map (comp create-node first)) without-match))))))))