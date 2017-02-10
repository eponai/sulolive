(ns eponai.common.ui.om-quill
  (:require
    #?(:cljs
       [cljsjs.quill])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defn get-contents [editor]
  (.getContents editor))

;(defn delta->html [delta-map]
;  #?(:cljs (js/Quill )))

(defn toolbar-opts []
  #?(:cljs
     (clj->js [[{"size" ["small" false "large"]}],
               ["bold", "italic", "underline", "strike"],
               [{"list" "ordered"}, {"list" "bullet"}, {"indent" "-1"}, {"indent" "+1"}],
               ;["link"],
               [{"color" []} {"background" []}]
               ["clean"]
               ])),)
(defui QuillEditor
  Object
  (componentDidMount [this]
    (let [{:keys [on-editor-created]} (om/get-computed this)
          {:keys [content]} (om/props this)]
      #?(:cljs
         (let [element (.getElementById js/document "quill-editor")
               editor (js/Quill. element (clj->js
                                           {:theme       "snow"
                                            :modules     {:toolbar (toolbar-opts)}
                                            :placeholder "What's your product like?"}))
               ;parchment (js/Quill.import "parchment")
               ;quill-style (.. parchment -Attributor -Style)
               ]
           ;(js/Quill.register (quill-style "size", "font-size", #js {:scope (.. parchment -Scope -INLINE)}), true)
           (.setContents editor (js/JSON.parse content))
           (when on-editor-created
             (on-editor-created editor))))))
  (render [this]
    (dom/div nil
      (dom/div #js {:id "quill-editor" :className "sl-quill-editor"}))))

(def ->QuillEditor (om/factory QuillEditor))