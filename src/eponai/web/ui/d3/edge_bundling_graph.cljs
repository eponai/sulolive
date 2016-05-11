(ns eponai.web.ui.d3.edge-bundling-graph
  (:require
    [cljsjs.d3]
    [cljs-time.coerce :as c]
    [cljs-time.core :as t]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))
(def test-data [{:name "thailand" :children ["food" "dinner" "coffee"]}
                {:name "dinner" :children ["thailand" "japan" "osaka" "food"]}
                {:name "japan" :children ["osaka" "coffee"]}
                {:name "coffee" :children ["japan" "osaka" "thailand"]}
                {:name "osaka" :children ["japan" "coffee" "dinner"]}
                {:name "food" :children ["dinner" "japan" "osaka" "thailand"]}])
(def test2 [{:name "thailand" :children ["food" "dinner"]}
            {:name "food" :children ["dinner"]}
            {:name "dinner" :children ["thailand"]}])

(defn traverse-children [nodes current]
  (let [new-node (get @nodes (:name current))]
    (if new-node
      (let [new-node-children (:children new-node)
            _ (swap! nodes #(dissoc % (:name current)))
            children (filterv
                       #(some? %)
                       (mapv (fn [d]
                               (traverse-children nodes {:name d}))
                             new-node-children))]
        (if (seq children)
          (assoc current :children children)
          current))
      nil)))

(defn data->tree [data]
  (let [nodes (atom (reduce
                      (fn [m me] (assoc m (:name me) me))
                      {}
                      data))
        root (first data)]
    ;(swap! nodes #(dissoc % (:name root)))

    {:name "" :children [(traverse-children nodes (first data))]}))
(defn create-links [nodes tree-data]
  (let [no-root (rest nodes)
        by-name (reduce (fn [m n]
                          (assoc m (.-name n) n))
                        {}
                        no-root)
        ret (reduce (fn [links n]
                      (if-let [children (:children n)]
                        (let [child-links (map (fn [child]
                                                 {:source (get by-name (:name n)) :target (get by-name child)})
                                               children)]
                          (reduce (fn [l c]
                                    (conj l c))
                                  links
                                  child-links))
                        links))
                    []
                    tree-data)]
    ret))

(defn rad->deg [rad]
  (* (/ rad 180) js/Math.PI))

(defui EdgeBundling
  Object
  (create [this]
    (let [{:keys [id width height data]} (om/props this)
          {:keys [margin]} (om/get-state this)
          svg (d3/build-svg (str "#edge-bundling-" id) width height)
          {inner-height :height
           inner-width :width} (d3/svg-dimensions svg {:margin margin})

          outer-radius (/ (min inner-height inner-width) 2)
          inner-radius (- outer-radius 50)
          cluster (.. js/d3 -layout cluster
                      (size #js [360 inner-radius])
                      (sort nil)
                      (value (fn [d] (.-name d))))
          bundle (.. js/d3 -layout bundle)
          line (.. js/d3
                   -svg -line radial
                   (interpolate "bundle")
                   (tension ".5")
                   (radius (fn [d] (.-y d)))
                   (angle (fn [d] (rad->deg (.-x d)))))
          new-test-data (mapv (fn [n] {:name (:name n) :children (mapv #(do {:name %}) (:children n))}) (:values (first data)))
          my-nodes (clj->js {:name "" :children (mapv (fn [n] {:name (:name n)}) (:values (first data)))})
          ;_ (debug "My nods: " my-nodes)

          ns (.. cluster (nodes my-nodes))
          _ (debug "My ns: " ns)
          _ (debug "Bulding links: " (create-links ns (:values (first data))))
          ls (clj->js (create-links ns (:values (first data))))
          graph (.. svg
                    (append "g")
                    (attr "transform" (str "translate(" (/ inner-width 2) "," (/ inner-height 2) ")")))
          link  (.. graph
                    (selectAll ".link")
                    (data (bundle ls)))
          node (.. graph
                   (selectAll ".node")
                   (data ns))
          mousover (fn [d]
                     (.. node
                         (each (fn [n]
                                 (set! (.-target n) false)
                                 (set! (.-source n) false))))
                     (.. link
                         (classed "link--target" (fn [l]
                                                   (when (= d (.-target l))
                                                     (set! (.-source (.-source l)) true)
                                                     true)))
                         (classed "link--source" (fn [l]
                                                   (when (= d (.-source l))
                                                     (set! (.-target (.-target l)) true)
                                                     true)))
                         (filter (fn [l] (or (= (.-target l) d) (= (.-source l) d))))
                         (each (fn []
                                 (this-as jthis
                                   (.appendChild (.-parentNode jthis) jthis))))
                         )
                     (.. node
                         (classed "node--target" (fn [n] (.-target n)))
                         (classed "node--source" (fn [n] (.-source n))))
                     )
          mouseout (fn [_]
                    (.. link
                        (classed "link--target" false)
                        (classed "link--source" false))
                    (.. node
                        (classed "node--target" false)
                        (classed "node--source" false)))]
      (.. link
          enter
          (append "path")
          (each (fn [d]
                  (set! (.-source d) (first d))
                  (set! (.-target d) (last d))))
          (attr "class" "link")
          (attr "d" line))

      (.. node
          enter
          (append "g")
          (attr "class" "node")
          (attr "transform" (fn [d] (str "rotate(" (- (.-x d) 90) ")translate(" (.-y d) ")")))
          (append "text")
          (attr "dx" (fn [d] (if (< (.-x d) 180)
                               8
                               -8)))
          (attr "dy" ".31em")
          (attr "text-anchor" (fn [d]
                                (if (< (.-x d) 180)
                                  "start"
                                  "end")))
          (attr "transform" (fn [d] (when
                                      (>= (.-x d) 180)
                                      "rotate(180)")))
          (text (fn [d] (.-name d)))
          (on "mouseover" mousover)
          (on "mouseout" mouseout))))
  (update [this]
    )
  (initLocalState [_]
    {:margin {:top 0 :bottom 20 :left 0 :right 0}})
  (componentDidMount [this]
    (d3/create-chart this))

  (componentDidUpdate [this _ _]
    (d3/update-chart this))

  (componentWillReceiveProps [this next-props]
    (d3/update-chart-data this (:data next-props)))

  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         (opts {:id    (str "edge-bundling-" id)
                :style {:height "100%"
                        :width  "100%"}})]))))

(def ->EdgeBundling (om/factory EdgeBundling))
