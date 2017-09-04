(ns eponai.server.ui.html
  (:require [om.dom :as dom]
            [om.next.protocols :as om.protocols]))

(deftype HtmlString [html]
  om.protocols/IReactDOMElement
  (-render-to-string [this react-id sb]
    (dom/append! sb html)))

(defn raw-string [html]
  {:pre [(string? html)]}
  (HtmlString. html))

(declare render-html!)

(defn render-element!
  "Render a tag vector as a HTML element string."
  [{:keys [tag attrs children]} react-id ^StringBuilder sb]
  (dom/append! sb "<" tag)
  (dom/render-attr-map! sb tag attrs)
  (if (dom/container-tag? tag (seq children))
    (do
      (dom/append! sb ">")
      (if-let [html-map (:dangerouslySetInnerHTML attrs)]
        (dom/render-unescaped-html! sb html-map)
        (run! #(render-html! % react-id sb) children))
      (dom/append! sb "</" tag ">"))
    (dom/append! sb "/>")))

(defn- render-html! [x react-id ^StringBuilder sb]
  (condp = (type x)
    ;; Special case for elements to avoid including react-id tags.
    om.dom.Element (render-element! x react-id sb)
    ;;; Special case raw html:
    ;HtmlString (dom/append! sb (.-html ^HtmlString x))
    ;; Else:
    (om.protocols/-render-to-string x react-id sb)))

(defn render-html-without-reactid-tags ^StringBuilder [x]
  {:pre [(or (satisfies? om.protocols/IReactComponent x)
             (satisfies? om.protocols/IReactDOMElement x))]}
  (let [element (if-let [element (cond-> x (satisfies? om.protocols/IReactComponent x) (#'dom/render-component))]
                  element
                  (#'dom/react-empty-node))
        sb (StringBuilder.)]
    (render-html! element (volatile! 1) sb)
    sb))
