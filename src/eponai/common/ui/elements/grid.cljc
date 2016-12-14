(ns eponai.common.ui.elements.grid
  (:require
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]))

(defn grid-element* [{:keys [classes]} & content]
  (apply dom/div #js {:className (css/keys->class-str classes)}
         content))

(defn row [{:keys [classes]} & content]
  (apply grid-element*
         {:classes (conj classes ::css/grid-row)}
         content))

(defn column [{:keys [classes]} & content]
  (apply grid-element*
         {:classes (conj classes ::css/grid-column)}
         content))

(defn row-column [{:keys [classes]} & content]
  (apply grid-element*
         {:classes (conj classes ::css/grid-column ::css/grid-row)}
         content))
