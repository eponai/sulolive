(ns eponai.web.ui.about-sulo
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as photo]))

(defui AboutSulo
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}])
  Object
  (render [this]
    (let [{:query/keys [navbar]} (om/props this)]
      (common/page-container
        {:navbar navbar :id "sulo-about"}
        (grid/row-column
          nil
          (dom/div
            (css/add-class :section-title)
            (dom/h1 nil "About us"))
          (dom/p
            (css/text-align :center)
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.")
          (dom/div (css/add-class :section-title)
                   (dom/h2 nil "The team"))
          (grid/row
            (grid/columns-in-row {:small 1 :medium 3})
            (grid/column
              (css/text-align :center)
              (dom/h3 nil "Miriam")
              (photo/square {:photo-id "static/about-miriam"})
              (dom/p nil (dom/span nil "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book.")))
            (grid/column
              (css/text-align :center)
              (dom/h3 nil "Petter")
              (photo/square {:photo-id "static/about-petter"})
              (dom/p nil (dom/span nil "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book.")))
            (grid/column
              (css/text-align :center)
              (dom/h3 nil "Diana")
              (photo/square {:photo-id "static/about-diana"})
              (dom/p nil (dom/span nil "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book.")))))))))

(router/register-component :about AboutSulo)