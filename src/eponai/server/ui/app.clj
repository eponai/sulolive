(ns eponai.server.ui.app
  (:require [om.next :as om :refer [defui]]
            [om.dom :as dom]
            [eponai.server.ui.common :as common :refer [text-javascript]]))

(defui App
  Object
  (render [this]
    (let [{:keys [release?]} (om/props this)]
      (dom/html
       nil
       (apply dom/head nil (common/head release?))

       (dom/body
         nil
         (dom/div {:id "jm-app"}
           (dom/div {:id "jm-ui"}
             (dom/div {:id "nav-container"}
               (dom/div {:id "topnav" :className "top-bar-container"}
                 (dom/div {:className "top-bar"}
                   (dom/div {:className "top-bar-left"}
                     (dom/a {:className "navbar-brand"} "jourmoney"))))
               (dom/div {:id "subnav" :className "subnav top-bar-container"}
                 (dom/div {:className "row column"}
                   (dom/div {:className "top-bar"}))))
             (dom/div {:id "page-content"})
             (common/footer)))
         (dom/script {:src (if release?
                             "/release/js/out/budget.js"
                             "/dev/js/out/budget.js")
                      :type text-javascript})
         (common/inline-javascript ["env.web.main.run();"])
         (dom/script {:type text-javascript
                      ;; Should we run stripe in non-release?
                      :src "https://js.stripe.com/v2/"}))))))

