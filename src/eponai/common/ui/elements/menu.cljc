(ns eponai.common.ui.elements.menu
  (:require
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]))

;; Menu elements
(defn- menu*
  "Custom menu element with provided content. For the provided content it's
  recommended to use any if the item- functions to generate compatible elements.
  Opts
  :classes - class keys to apply to this menu element.

  See css.cljc for available class keys."
  [{:keys [classes]} & content]
  (apply dom/ul #js {:className (css/keys->class-str (conj classes ::css/menu))}
         content))

(defn horizontal
  "Menu in horizontal layout.

  See menu* for general opts and recommended content."
  [{:keys [classes]} & content]
  (apply menu*
         {:classes classes}
         content))

(defn vertical
  "Menu in vertical layout.
  See menu* for general opts and recommended content."
  [{:keys [classes]} & content]
  (apply menu*
         {:classes (conj classes ::css/menu-vertical)}
         content))

;; Menu list item elements
(defn- item* [{:keys [classes]} & content]
  (apply
    dom/li #js {:className (css/keys->class-str classes)}
    content))

(defn item
  "Custom menu item containing the provided content.

  Opts
  :classes - what class keys should be added to this item.

  See css.cljc for available class keys."
  [opts & content]
  (apply item* opts content))

(defn item-tab
  "Menu item representing a tab in some sort of stateful situation.

  Opts
  :active? - Whether this tab is in an active state.
  :on-click - Function called when item is clicked, this can be used to update any state.

  See item for general opts."
  [{:keys [classes active? on-click]} & content]
  (item*
    {:classes (cond-> classes
                      active?
                      (conj ::css/menu-active))}
    (apply dom/a
           #js {:onClick on-click}
           content)))

(defn item-link
  "Menu item containing an anchor link.

  Opts
  :href - href for the containng anchor

  See item for general opts."
  [{:keys [classes href]} & content]
  (item*
    {:classes classes}
    (apply dom/a
           #js {:href href}
           content)))

(defn item-dropdown
  "Menu item containg a link that opens a dropdown.
  Accepts a :dropdown key in opts containing the actual dropdown content element."
  [{:keys [dropdown href classes]} & content]
  (item*
    {:classes (conj classes ::css/menu-dropdown)}
    (apply dom/a #js {:href href} content)
    dropdown))

(defn item-text
  "Menu item element containing text only.

  See item for general opts."
  [{:keys [classes]} & content]
  (apply item*
         {:classes (conj classes ::css/menu-text)}
         content))