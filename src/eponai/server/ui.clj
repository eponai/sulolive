(ns eponai.server.ui
  (:require [om.next :as om]
            [om.dom :as dom]
            [eponai.server.ui.app :as app]
            [eponai.server.ui.index :as index]
            [eponai.server.ui.signup :as signup]))

(defn with-doctype [html-str]
  (str "<!DOCTYPE html>" html-str))

(defn render-to-str [component props]
  (with-doctype (dom/render-to-str ((om/factory component) props))))

(defn app-html [props]
  (render-to-str app/App props))

