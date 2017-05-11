(ns eponai.web.ui.store.common
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [om.next :as om]
    [eponai.common.ui.elements.menu :as menu]))

(defn edit-button [opts & content]
  (dom/a
    (->> (css/button-hollow opts)
         (css/add-class :shrink)
         (css/add-class :secondary))
    (dom/i {:classes ["fa fa-pencil  fa-fw"]})
    (if (not-empty content)
      content
      (dom/span nil "Edit"))))

(defn save-button [opts & content]
  (dom/a (css/button opts)
         (dom/span nil "Save")))

(defn cancel-button [opts & content]
  (dom/a (css/button-hollow opts)
         (dom/span nil "Cancel")))

(defprotocol IDashboardNavbarContent
  (render-subnav [this current-route] "Return the component's content for the sub navbar in store dashboard"))


(defn edit-sections-modal [component {:keys [edit-sections items]}]
  (common/modal
    {:on-close #(om/update-state! component dissoc :products/edit-sections)}
    (let [items-by-section (group-by #(get-in % [:store.item/section :db/id]) items)]
      (dom/div
        nil
        (dom/p (css/add-class :header) "Edit sections")
        (menu/vertical
          (css/add-class :edit-sections-menu)
          (map-indexed
            (fn [i s]
              (let [no-items (count (get items-by-section (:db/id s)))]
                (menu/item (css/add-class :edit-sections-item)
                           ;(dom/a nil (dom/i {:classes ["fa "]}))
                           (dom/input
                             {:type        "text"
                              :id          (str "input.section-" i)
                              :placeholder "New section"
                              :value       (:store.section/label s "")
                              :onChange    #(om/update-state! component update :products/edit-sections
                                                              (fn [sections]
                                                                (let [old (get sections i)
                                                                      new (assoc old :store.section/label (.-value (.-target %)))]
                                                                  (assoc sections i new))))})
                           (if (= 1 no-items)
                             (dom/small nil (str no-items " item"))
                             (dom/small nil (str no-items " items")))
                           (dom/a
                             (->> {:onClick #(om/update-state! component update :products/edit-sections
                                                               (fn [sections]
                                                                 (into [] (remove nil? (assoc sections i nil)))))}
                                  (css/button-hollow)
                                  (css/add-class ::css/color-secondary))
                             (dom/i {:classes ["fa fa-trash-o fa-fw"]})))))
            edit-sections))

        (dom/a (css/button-hollow {:onClick #(om/update-state! component update :products/edit-sections conj {})})
               (dom/i {:classes ["fa fa-plus-circle fa-fw"]})
               (dom/span nil "Add section"))
        (dom/hr nil)
        (dom/div
          (css/text-align :right)
          (cancel-button {:onClick #(om/update-state! component dissoc :products/edit-sections)})
          (save-button {:onClick #(.save-sections component)}))))))