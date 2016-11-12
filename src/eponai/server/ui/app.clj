(ns eponai.server.ui.app
  (:require
    [eponai.server.ui.common :as common :refer [text-javascript]]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui App
  Object
  (render [this]
    (let [{:keys [release? playground?]} (om/props this)]
      (dom/html
       nil
       (apply dom/head nil (common/head release?))

       (dom/body
         nil
         (common/anti-forgery-field)
         (dom/div (cond-> {:id "jm-app"}
                          playground?
                          (assoc :className "jm-playground"))
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
         (if playground?
           (common/inline-javascript ["env.web.main.runplayground()"])
           (common/inline-javascript ["env.web.main.run();"]))

         (when-not playground?
           (dom/script {:type text-javascript
                        ;; Should we run stripe in non-release?
                        :src  "https://js.stripe.com/v2/"})))))))

