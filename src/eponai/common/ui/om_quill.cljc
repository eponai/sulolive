(ns eponai.common.ui.om-quill
  (:require
    #?(:cljs
       [cljsjs.quill])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defn get-contents [editor]
  #?(:cljs
     (js/JSON.stringify (.getContents editor))))

(defn get-HTML [editor]
  #?(:cljs
     (.. editor -root -innerHTML)))

;(defn delta->html [delta-map]
;  #?(:cljs (js/Quill )))

(defn toolbar-opts []
  #?(:cljs
     (clj->js [[{"size" ["small" false "large"]}],
               ["bold", "italic", "underline", "strike"],
               [{"list" "ordered"}, {"list" "bullet"}, {"indent" "-1"}, {"indent" "+1"}],
               [{"align" []}]
               ;["link"],
               [{"color" []} {"background" []}]
               ["clean"]
               ])),)
(defui QuillEditor
  Object
  (componentDidMount [this]
    (let [{:keys [on-editor-created]} (om/get-computed this)
          {:keys [content placeholder theme]} (om/props this)]
      #?(:cljs
         (let [element (.getElementById js/document "quill-editor")
               editor (js/Quill. element (clj->js
                                           {:theme       (or theme "snow")
                                            :modules     {:toolbar (toolbar-opts)}
                                            :placeholder placeholder}))
               ;parchment (js/Quill.import "parchment")
               ;quill-style (.. parchment -Attributor -Style)
               ]
           ;(js/Quill.register (quill-style "size", "font-size", #js {:scope (.. parchment -Scope -INLINE)}), true)
           ;(.setContents editor (js/JSON.parse content))
           (.. editor -clipboard (dangerouslyPasteHTML content))
           (when on-editor-created
             (on-editor-created editor))))))
  (render [this]
    (dom/div #js {:id "quill-editor-container" :className "rich-text-input"}
      (dom/div #js {:id "quill-editor" :className "sl-quill-editor"}))))

(def ->QuillEditor (om/factory QuillEditor))

(defui QuillRenderer
  Object
  (render [this]
    (let [{:keys [html]} (om/props this)]
      (dom/div
        #js {:className "ql-editor"}
        (dom/div
          #js {:dangerouslySetInnerHTML #js {:__html html}})))))

(def ->QuillRenderer (om/factory QuillRenderer))