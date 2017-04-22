(ns eponai.common.ui.elements.table
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]))

(defn table [opts & content]
  (dom/div (css/add-class :table opts) content))

(defn thead [opts & content]
  (dom/div (css/add-class :thead opts) content))

(defn tfoot [opts & content]
  (dom/div (css/add-class :tfoot opts) content))

(defn tbody [opts & content]
  (dom/div (css/add-class :tbody opts) content))

(defn thead-row [opts & content]
  (dom/div (css/add-class :tr opts) content))

(defn tbody-row [opts & content]
  (dom/a (css/add-class :tr opts) content))

(defn th [opts & content]
  (dom/span (css/add-class :th opts) content))

(defn td [opts & content]
  (dom/div (css/add-class :td opts) content))
