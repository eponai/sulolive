(ns eponai.web.ui.utils.infinite-scroll
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [cljsjs.react.dom]
            [taoensso.timbre :refer-macros [trace debug error warn]]))

(def default-list-size-increment 10)
(def default-initial-list-size 50)
(def default-list-increment-threshold-px 700)

(defprotocol IContainerChild
  (container-parent [this]))

(defprotocol IHaveListener
  (init-listener [this])
  (get-listener [this])
  (attach-listener [this])
  (detach-listener [this])
  (call-listener [this]))

(defn- top-offset-to-window [node]
  (->> (iterate #(.-offsetParent %) node)
       (take-while some?)
       (map #(.-offsetTop %))
       (reduce + 0)))

(defn scroll-listener [this container-node]
  (fn []
    (trace "Calling scroll listener")
    (when (> (count (:elements (om/props this)))
             (:list-size (om/get-state this)))
      (when container-node
        ;; Test if the distance between the list's bottom
        ;; and the container's bottom is short enough.
        ;; If it is, then update the list size.
        (let [{:keys [list-size-increment]} (om/props this)
              list-node (js/ReactDOM.findDOMNode this)
              ;; Container bottom:
              container-bottom (.-offsetHeight container-node)

              ;; Normalize the list's height with the container, using
              ;; their top position.
              list-top (top-offset-to-window list-node)
              container-top (top-offset-to-window container-node)
              top-delta (- list-top container-top)

              ;; List bottom relative to the container bottom:
              list-height (.-offsetHeight list-node)
              scrolled-offset (.-scrollTop container-node)
              list-bottom (- list-height top-delta scrolled-offset)

              ;; Calc the distance
              distance-between-list-and-container-bottom (- list-bottom container-bottom)

              ;; We want to increase the list-size before the bottom of the list
              ;; is aligned with the bottom of the container, to be able to scroll
              ;; continously.
              threshold (or (:list-increment-threshold-px (om/props this))
                            default-list-increment-threshold-px)]
          (when (> threshold distance-between-list-and-container-bottom)
            ;; (om/update-state! this update :list-size + (or list-size-increment default-list-size-increment))
            (debug "Increased infinite scroll list size to: " (:list-size (om/get-state this)))))))))

(defui InfiniteScroll
  IContainerChild
  (container-parent [this]
    (when-let [f (:dom-node-fn (om/get-computed (om/props this)))]
      (f)))

  IHaveListener
  (init-listener [this]
    (let [container-node (container-parent this)]
      (om/update-state! this assoc
                        :scroll-listener (scroll-listener this container-node)
                        :container-node container-node)))

  (get-listener [this]
    (:scroll-listener (om/get-state this)))

  (attach-listener [this]
    (when-let [container (container-parent this)]
      (let [listener (get-listener this)]
        (.addEventListener container "scroll" listener)
        (.addEventListener container "resize" listener))))

  (detach-listener [this]
    (when-let [listener (get-listener this)]
      (when-let [container (container-parent this)]
        (.removeEventListener container "scroll" listener)
        (.removeEventListener container "resize" listener))))

  (call-listener [this]
    ((get-listener this)))

  Object
  (initLocalState [this]
    {:list-size (or (:initial-list-size (om/props this))
                    default-initial-list-size)})

  (componentDidMount [this]
    (init-listener this)
    (attach-listener this)
    ;; Call listener when mounted to the dom.
    ;; Later the listener will be called in componentDidUpdate.
    (call-listener this))

  (componentWillUnmount [this]
    (detach-listener this))

  (componentDidUpdate [this _ prev-state]
    (if (not= (container-parent this)
              (:container-node prev-state))
      ;; When the container has changed, re-attach the listener
      ;; and this method is called again, which will call the listener.
      (do (detach-listener this)
          (init-listener this)
          (attach-listener this))
      (call-listener this)))

  (render [this]
    (let [{:keys [elements elements-container]} (om/props this)]
      (assert (counted? elements))
      (html
        [(or elements-container :div)
         (take (:list-size (om/get-state this))
               elements)]))))

(def ->InfiniteScroll (om/factory InfiniteScroll))
