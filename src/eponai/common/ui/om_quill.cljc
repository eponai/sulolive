(ns eponai.common.ui.om-quill
  (:require
    #?(:cljs
       [cljsjs.quill])
    #?(:clj
       [autoclave.core :as a])
       #?(:cljs dompurify)
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(defn get-contents [editor]
  #?(:cljs
     (js/JSON.stringify (.getContents editor))))

(defn sanitize-html [dirty-html]
  (when (not-empty dirty-html)
    (let [allowed-classes ["ql-align-right" "ql-align-center" "ql-size-small" "ql-size-large"]
          allowed-tags ["p" "span" "strong" "ul" "ol" "li" "u" "s" "i" "em" "br"]]

      #?(:cljs (let [policy (clj->js {:ALLOWED_TAGS allowed-tags})]
                 (js/DOMPurify.sanitize dirty-html policy))
         :clj  (let [policy (a/html-policy :allow-common-inline-formatting-elements
                                           :allow-common-block-elements
                                           :allow-attributes ["class"
                                                              :globally
                                                              :matching [(fn [_ _ value]
                                                                           (some #(when (clojure.string/includes? value %) %)
                                                                                 allowed-classes))]])]
                 (a/html-sanitize policy dirty-html))))))

(defn get-HTML #?@(:cljs [[^js/Quill editor]
                          (sanitize-html (.. editor -root -innerHTML))]
                   :clj[[editor] nil]))

(defn toolbar-opts []
  #?(:cljs
     (clj->js [
               ;[{"size" ["small" false "large"]}],
               ["bold", "italic", "underline"],
               [{"list" "ordered"}, {"list" "bullet"}],
               [{"align" []}]
               ;["link"],
               ;[{"color" []} {"background" []}]
               ["clean"]
               ])),)
(defui QuillEditor
  Object
  (componentDidMount [this]
    (let [{:keys [on-editor-created]} (om/get-computed this)
          {:keys [content placeholder theme id]} (om/props this)]
      #?(:cljs
         (let [element (.getElementById js/document (str id "-quill-editor"))
               editor (js/Quill. element (clj->js
                                           {:theme       (or theme "snow")
                                            :modules     {:toolbar (toolbar-opts)}
                                            :placeholder placeholder}))
               ;parchment (js/Quill.import "parchment")
               ;quill-style (.. parchment -Attributor -Style)
               ]
           ;(js/Quill.register (quill-style "size", "font-size", #js {:scope (.. parchment -Scope -INLINE)}), true)
           ;(.setContents editor (js/JSON.parse content))
           (.. editor -clipboard (dangerouslyPasteHTML (sanitize-html content)))
           (when on-editor-created
             (on-editor-created editor))))))
  (render [this]
    (let [{:keys [id]} (om/props this)]
      (dom/div #js {:id (str id "-quill-editor-container") :className "sl-quill-editor-container rich-text-input"}
        (dom/div #js {:id (str id "-quill-editor") :className "sl-quill-editor"})))))

(def ->QuillEditor (om/factory QuillEditor))

(defui QuillRenderer
  Object
  (render [this]
    (let [{:keys [html]} (om/props this)]
      (dom/div
        #js {:className               "ql-editor"
             :dangerouslySetInnerHTML #js {:__html (sanitize-html html)}}))))

(def ->QuillRenderer (om/factory QuillRenderer))