(ns eponai.client.ui.om-quill
  (:require
    [cljsjs.quill]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui QuillEditor
  Object
  (componentDidMount [this]
    (let [element (.getElementById js/document "quill-editor")]
      (js/Quill. element #js {:theme "snow"
                              :modules #js {:toolbar "#quill-toolbar"}
                              :placeholder "What's your product like?"})))
  (render [this]
    (dom/div nil

      ;<div id="toolbar">
      ;<!-- Add font size dropdown -->
      ;<select class="ql-size">
      ;<option value="small"></option>
      ;<!-- Note a missing, thus falsy value, is used to reset to default -->
      ;<option selected></option>
      ;<option value="large"></option>
      ;<option value="huge"></option>
      ;</select>
      ;<!-- Add a bold button -->
      ;<button class="ql-bold"></button>
      ;<!-- Add subscript and superscript buttons -->
      ;<button class="ql-script" value="sub"></button>
      ;<button class="ql-script" value="super"></button>
      ;</div>
      (dom/div #js {:id "quill-toolbar" :className "sl-quill-toolbar"}
        (dom/select #js {:className "ql-size"}
                    (dom/option #js {:value "small"})
                    (dom/option #js {:value "medium"})
                    (dom/option #js {:value "large"}))
        (dom/button #js {:className "ql-bold"} "B")
        (dom/button #js {:className "ql-italic"} "I")
        (dom/button #js {:className "ql-underline ql-format-button"} "U")
        (dom/button #js {:className "ql-script" :value "sub"})
        (dom/button #js {:className "ql-script" :value "super"})
        )
      (dom/div #js {:id "quill-editor" :className "sl-quill-editor"}))))

(def ->QuillEditor (om/factory QuillEditor))
