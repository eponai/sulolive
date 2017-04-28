(ns eponai.common.ui.help
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.encoding-guide :as encoding]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.navbar :as nav]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]))

(defui Help
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     :query/current-route])
  Object
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [current-route]} (om/props this)
          {:keys [route]} current-route]
      (common/page-container
        {:navbar navbar :id "sulo-help"}
        (grid/row-column
          nil
          (dom/div
            (css/add-class :app-title)
            (dom/a
              nil
              (dom/span nil "SULO Live help")))
          (cond (= route :help/encoding) (encoding/->EncodingGuide)
                :else
                (grid/row-column
                  nil
                  (callout/callout
                    (css/add-class :large)
                    ;(dom/article
                    ;  nil
                    ;  (dom/section
                    ;    nil))
                    (dom/h1 nil "SULO Live help")
                    (dom/h2 nil "Menu")
                    (menu/vertical
                      nil
                      (menu/item nil
                                 (dom/strong nil "General")
                                 (dom/ul
                                   (css/add-class :nested)
                                   (dom/li
                                     nil
                                     (dom/p nil
                                            (dom/a {:href (routes/url :help/fees)}
                                                   (dom/span nil "SULO Live service fee"))))))
                      (menu/item nil
                                 (dom/strong nil "Live streaming")
                                 (dom/ul
                                   (css/add-class :nested)
                                   (dom/li nil
                                           (dom/p nil
                                                  (dom/a {:href (routes/url :help/encoding)}
                                                         (dom/span nil "Setup your encoder")))))))

                    (dom/h2 nil "Contact us")
                    (dom/p nil (dom/span nil "Do you still have questions? Contact us on ")
                           (dom/a {:href "mailto:help@sulo.live"} "help@sulo.live")
                           (dom/span nil ". We're happy to help!"))))))))))

(def ->Help (om/factory Help))