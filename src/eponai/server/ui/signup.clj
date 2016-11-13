(ns eponai.server.ui.signup
  (:require
    [eponai.server.ui.common :as common :refer [text-javascript]]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Signup
  Object
  (render [this]
    (let [{:keys [release?]} (om/props this)]
      (dom/html
        {:lang "en"}
        (dom/head
          nil
          (common/head release? true))
        (dom/body
          nil
          (common/inline-javascript (common/iubenda-code))
          (common/inline-javascript (common/facebook-async-init-code))

          (common/anti-forgery-field)
          (dom/div {:id "signup-page"}
            (dom/div {:id "jm-ui"}
              (dom/div {:id "header"}
                (dom/nav {:className "top-bar"}
                         (dom/div {:className "top-bar-left"}
                           (dom/a {:className "navbar-brand" :href "/"}
                                  (dom/strong nil "jourmoney"))))
                (dom/div {:className "intro intro-message"}
                  (dom/div {:id "jm-signup"})))
              (common/footer))))
        (dom/script {:src "/dev/js/out/budget.js"
                     :type text-javascript})
        (common/inline-javascript ["env.web.main.runsignup()"])))))
