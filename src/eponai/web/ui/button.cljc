(ns eponai.web.ui.button
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]))

(defn button [opts & content]
  (dom/a (css/add-class :button opts) content))

(defn button-small [opts & content]
  (button (css/add-class :small opts) content))

(defn secondary [& [opts]]
  (css/add-class :secondary opts))

(defn hollow [& [opts]]
  (css/add-class :hollow opts))

(defn sulo-dark [& [opts]]
  (css/add-class :sulo-dark opts))

(defn expanded [& [opts]]
  (css/add-class :expanded opts))

(defn small [& [opts]]
  (css/add-class :small opts))


(defn default [opts & content]
  (button
    (css/add-classes [:secondary] opts)
    content))

(defn default-hollow [opts & content]
  (default (css/add-class :hollow opts) content))


(defn user-setting-default [opts & content]
  (button (css/add-classes [:secondary :small :hollow] opts) content))

(defn user-setting-cta [opts & content]
  (button (css/add-classes [:secondary :small] opts) content))

(defn store-navigation-default [opts & content]
  (button (css/add-classes [:sulo-dark :hollow] opts) content))

(defn store-navigation-cta [opts & content]
  (button (css/add-classes [:sulo-dark] opts) content))

(defn store-setting-default [opts & content]
  (button (css/add-classes [:sulo-dark :small :hollow] opts) content))

(defn store-setting-secondary [opts & content]
  (button (css/add-classes [:secondary :small :hollow] opts) content))

(defn store-setting-cta [opts & content]
  (button (css/add-classes [:sulo-dark :small] opts) content))

(defn edit [opts & content]
  (store-setting-secondary opts (or content
                                  [(dom/i {:classes ["fa  fa-pencil fa-fw"]}) (dom/span nil "Edit")])))

(defn cancel [opts & content]
  (store-setting-secondary opts (or content (dom/span nil "Cancel"))))

(defn save [opts & content]
  (store-setting-cta opts (or content (dom/span nil "Save"))))

