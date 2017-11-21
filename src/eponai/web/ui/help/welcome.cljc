(ns eponai.web.ui.help.welcome
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.css :as css]))

(defui Welcome
  Object
  (render [this]
    (let [{:active/keys [what-is-sulo]} (om/get-state this)]
      (dom/div
        nil
        (callout/callout-small
          (css/add-class :help-menu)
          (grid/row-column
            nil
            (menu/breadcrumbs
              nil
              (menu/item nil (dom/a {:href (routes/url :help)}
                                    (dom/span nil "SULO Live support")))
              (menu/item nil (dom/span nil "Welcome")))))

        (grid/row-column
          nil
          (dom/h1 nil (dom/span nil "Welcome") (dom/br nil) (dom/small nil "Getting started with SULO Live"))

          (callout/callout
            nil
            (dom/h2 nil "Info")
            (dom/div
              (css/add-class :help-items)
              (dom/section
                (when what-is-sulo (css/add-class :is-active))
                (dom/a
                  (->> {:onClick #(om/update-state! this update :active/what-is-sulo not)}
                       (css/add-class :header))
                  (dom/h3 nil "What is SULO Live?"))
                (dom/div
                  (css/add-class :help-item-content)
                  (dom/p nil "SULO Live is an e-commerce platform for people to start their own store and share their work and story via live stream to their customers. Anyone can visit SULO Live and view the stores' live streams and start an account to purchase their products. ")))
              )))))))
(def ->Welcome (om/factory Welcome))
