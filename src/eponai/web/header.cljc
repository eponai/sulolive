(ns eponai.web.header
  (:require [medley.core :as medley]
            [taoensso.timbre :refer [debug error]]))

(defn remove-empty-vals [m]
  (into {}
        (filter (comp some? val))
        m))

(defprotocol IHeadTag
  (tag-map [this]))

(defprotocol IWriteDOMNodes
  (tag-name [this])
  (mutate-node! [this node]))

(defrecord TitleTag [content]
  IHeadTag
  (tag-map [this]
    {:tag :title :content content})
  IWriteDOMNodes
  (tag-name [this] "title")
  (mutate-node! [this node]
    (when-not (= (.-innerText node) (str content))
      (set! (.-innerText node) (str content)))))

(defrecord DescriptionTag [content]
  IHeadTag
  (tag-map [this]
    {:tag :description :content content})
  IWriteDOMNodes
  (tag-name [this] "description")
  (mutate-node! [this node]
    (when-not (= (.-innerText node) (str content))
      (set! (.-innerText node) (str content)))))

(defn- set-node-attributes!
  "Takes an HTMLElement (node), a map and keys, and sets all keys as attributes
  on the node with values from the map."
  [node m attrs]
  (->> attrs
       (filter (comp some? #(get m %)))
       (run! (fn [k]
               (.setAttribute node (name k) (str (get m k)))))))

(defrecord MetaTag [id name property content]
  IHeadTag
  (tag-map [this]
    (remove-empty-vals
      {:tag :meta :id id :name name :property property :content content}))
  IWriteDOMNodes
  (tag-name [this] "meta")
  (mutate-node! [this node]
    (set-node-attributes! node this [:id :name :property :content])))

(defn meta-tag
  "Helper function for creating the meta tags.
   Called with (meta-tag :id \"foo\" :content 123)

   Must contain either id, name or property. Can be all of them."
  [& {:as kvs}]
  {:pre [(some #{:id :name :property} (keys kvs))]}
  (map->MetaTag kvs))

(defn map->tag [head-meta]
  (condp = (:tag head-meta)
    :title (map->TitleTag head-meta)
    :description (map->DescriptionTag head-meta)
    :meta (map->MetaTag head-meta)))

#?(:cljs
   (extend-type js/HTMLElement
     IHeadTag
     (tag-map [this]
       (remove-empty-vals
         {:node       this
          :tag        (-> (.-nodeName this)
                          (clojure.string/lower-case)
                          (keyword))
          :id         (.-id this)
          :name       (.-name this)
          :property   (.-property this)
          :content    (.-content this)
          :inner-text (.-innerText this)}))))

(defmulti route-meta (fn [route-map app-state] (:route route-map)))
(defmulti match-head-meta-to-nodes (fn [tag metas nodes] tag))

#?(:cljs
   (defn set-head! [route app-state]
     (let [head-meta (concat (route-meta :default app-state)
                             (route-meta route app-state))
           meta-by-tags (group-by :tag (map tag-map head-meta))

           root-head-node (first (array-seq (.getElementsByTagName js/document "head")))
           head-children-by-tag (->> root-head-node
                                     (.-children)
                                     (array-seq)
                                     (map tag-map)
                                     (group-by :tag))

           matched (mapcat #(match-head-meta-to-nodes %
                                                      (get meta-by-tags %)
                                                      (get head-children-by-tag %))
                           (keys meta-by-tags))]
       (debug "Matched: " matched)
       (->> matched
            ;; Apply changes in reverse, distinct by the node that's mutated.
            (reverse)
            (medley/distinct-by (fn [[head-meta node]]
                                  ;; If there's no matching node, distinct by the head-meta
                                  ;; to avoid creating duplicates.
                                  (or node head-meta)))
            (run! (fn [[head-meta node]]
                    (let [x-tag (map->tag head-meta)]
                      (if (some? node)
                        (let [node (:node node)
                              pre-mutate (tag-map node)]
                          (mutate-node! x-tag node)
                          (debug "Mutated node, before: " pre-mutate " after: " (tag-map node)))
                        (let [node (.createElement js/document (tag-name x-tag))]
                          (mutate-node! x-tag node)
                          (debug "Created node: " (tag-map node))
                          (.appendChild root-head-node node))))))))))

(defmethod match-head-meta-to-nodes :title
  [_ metas nodes]
  ;; Return the last head-meta with the first node.
  ;; There should only be one title
  (when (seq nodes)
    (assert (= 1 (count nodes))
            (str "There were more than 1 title tag in <head>: " nodes)))
  [[(last metas) (first nodes)]])

(defmethod match-head-meta-to-nodes :description
  [_ metas nodes]
  (when (seq nodes)
    (assert (= 1 (count nodes))
            (str "There were more than 1 description tag in <head>: " nodes)))
  [[(last metas) (first nodes)]])

(defmethod match-head-meta-to-nodes :meta
  [_ metas nodes]
  (debug "Metas: " metas)
  (debug "nodes: " nodes)
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

(defmethod route-meta :default
  [_ state]
  [(->TitleTag "your local marketplace online")
   (->DescriptionTag "it's the best place online.")
   (meta-tag :property "rawr" :content "bar")])

(defmethod route-meta :browse/all-items
  [_ state]
  [(->TitleTag "BROWSING ALL ITEMS :D")])

(defmethod route-meta :browse/gender
  [_ state]
  [(->TitleTag "BROWSING SOME GENDER :D")])

(defmethod route-meta :browse/category
  [_ state]
  [(->TitleTag "BROWSING SOME CATEGORY :D")])