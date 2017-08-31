(ns eponai.web.header
  (:require [medley.core :as medley]
            [taoensso.timbre :refer [debug error]]
            [om.dom :as dom])
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

(defn title-tag [title]
  {:tag     :title
   :content title})

(defn meta-tag
  "Helper function for creating the meta tags.
   Called with (meta-tag :id \"foo\" :content 123)

   Must contain either id, name or property. Can be all of them."
  [& {:as kvs}]
  {:pre [(some #{:id :name :property} (keys kvs))]}
  (into {:tag :meta}
        (filter (comp some? val))
        kvs))

(defn description-tag [description]
  (meta-tag :name "description" :content description))

(defmulti match-head-meta-to-nodes-by-tag (fn [tag metas nodes] tag))

(defn match-head-meta-to-dom-nodes
  "Matches head node data (maps with {:tag :meta :name \"foo\"}) to dom objects.
  Returns a seq of pairs with [head-meta dom-node?]. When dom-node is nil, that means
  a new dom-node should be created."
  [head-data dom-nodes]
  {:pre [(every? (every-pred map? (comp some? :tag)) head-data)
         (every? #(satisfies? IHeadTag %) dom-nodes)]}
  (let [meta-by-tag (group-by :tag head-data)
        nodes-by-tag (group-by :tag (map tag-map dom-nodes))]
    (->> (keys meta-by-tag)
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
                               (or node head-meta))))))

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

(defmulti route-meta (fn [route-map app-state after-merge?]
                       (when-let [r (:route route-map)]
                         (or (namespace r)
                             (name r)))))

(def default-head-meta [(title-tag "your local marketplace online")
                        (description-tag "it's the best place online.")
                        (meta-tag :property "rawr" :content "bar")
                        (meta-tag :name "some-name" :content "some content")])

(defmethod route-meta :default
  [_ state after-merge?]
  default-head-meta)

(defmethod route-meta "browse"
  [{:keys [route]} state after-merge?]
  (into default-head-meta
        [(title-tag (condp = route
                       :browse/all-items "browse all items"
                       :browse/gender "browse gender"
                       :browse/category "browse cat "
                       "BROWSING :D"))]))

;; CLJS
(defprotocol IUpdateHeader
  (update-header! [this route app-state after-merge?]))

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

(defn mutate-node! [{:keys [tag content] :as head-meta} node]
  (condp = tag
    :title (set-node-inner-text node content)
    :meta (set-node-attributes! node
                                head-meta
                                [:id :name :property :content])))

#?(:cljs
   (defn mutate-dom-nodes [head-to-node-matches]
     (let [head (dom-head)]
       (->> head-to-node-matches
            (run! (fn [[head-meta node]]
                    (if (some? node)
                      (let [node (:node node)
                            pre-mutate (tag-map node)]
                        (mutate-node! head-meta node)
                        (debug "Mutated node, before: " pre-mutate " after: " (tag-map node)))
                      (let [node (.createElement js/document (name (:tag head-meta)))]
                        (mutate-node! head-meta node)
                        (debug "Created node: " (tag-map node))
                        (.appendChild head node)))))))))

#?(:cljs
   (defn header
     "header-meta-fn is a 1-arity function returning a sequence of <head> tag children."
     ([] (header #(route-meta (:route-map %) (:app-state %) (:after-merge? %))))
     ([header-meta-fn]
      (let [cache (atom {})]
        (reify IUpdateHeader
          (update-header! [this route-map app-state after-merge?]
            (let [head-meta (into []
                                  (filter some?)
                                  (header-meta-fn {:route-map    route-map
                                                   :app-state    app-state
                                                   :after-merge? after-merge?}))]
              ;; Avoid updating the same header info again.
              (if (= head-meta (:head-meta @cache))
                (debug "Head-meta was equal to last time, won't run the update.")
                (do
                  (swap! cache assoc :head-meta head-meta)
                  (let [dom-nodes (->> (dom-head) (.-children) (array-seq))
                        matches (match-head-meta-to-dom-nodes head-meta dom-nodes)]
                    (mutate-dom-nodes matches)))))))))))

;; CLJ

(defn create-node [{:keys [tag content] :as head-meta}]
  (condp = tag
    :title (dom/title nil content)
    :meta (dom/meta (dissoc head-meta :tag))
    (throw (ex-info "No matching tag?: " {:head-meta head-meta}))))

#?(:clj
   (defn update-header [dom-head head-meta]
     (let [matches (match-head-meta-to-dom-nodes head-meta
                                                 (:children dom-head))
           {with-nodes true without-nodes false} (group-by (comp some? second) matches)]
       (update dom-head :children
               (fn [children]
                 (let [matches-by-node (into {}
                                             (map (juxt (comp :node second) identity))
                                             with-nodes)]
                   (-> []
                       (into (map (fn update-node [child]
                                    (if-let [match (get matches-by-node child)]
                                      (let [head-meta (first match)]
                                        (create-node head-meta))
                                      child)))
                             children)
                       (into (map (comp create-node first)) without-nodes))))))))