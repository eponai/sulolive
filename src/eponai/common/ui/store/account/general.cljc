(ns eponai.common.ui.store.account.general
  (:require
    [eponai.common.format :as f]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.om-quill :as quill]
    [om.next :as om]))

(defn public-profile [component]
  (let [{:keys [form-elements]} (om/get-state component)
        {:keys [store]} (om/get-computed component)
        {:store/keys [description]} store]
    (grid/row
      (css/align :center)
      (grid/column
        (->> (css/text-align :center)
             (grid/column-size {:small 6 :medium 4 :large 3}))
        (photo/store-photo store)
        (dom/a (css/button-hollow) "Upload Photo"))
      (grid/column
        (grid/column-size {:small 12})
        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 12 :large 2})
            (dom/label nil "Store name"))
          (grid/column
            nil
            (dom/input {:type         "text"
                        :id           (:store.info/name form-elements)
                        :defaultValue (:store/name store)})))
        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 12 :large 2})
            (dom/label nil "Short description"))
          (dom/div
            (css/grid-column)
            (dom/input
              (->> {:type         "text"
                    :placeholder  "Keep calm and wear pretty jewelry"
                    :id           (:store.info/tagline form-elements)
                    :defaultValue (:store/tagline store)}
                   (css/add-class :tagline-input)))))
        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 12 :large 2})
            (dom/label nil "About"))
          (grid/column
            nil
            (quill/->QuillEditor (om/computed {:content     (f/bytes->str description)
                                               :placeholder "What's your story?"}
                                              {:on-editor-created #(om/update-state! component assoc :quill-editor %)}))))))))