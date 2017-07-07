(ns eponai.common.ui.om-quill
  (:require
    #?(:cljs
       [cljsjs.quill])
    #?(:clj
        [autoclave.core :as a])
        [om.dom :as dom]
        [om.next :as om :refer [defui]]
        [taoensso.timbre :refer [debug error]]
        [eponai.common.ui.dom :as my-dom]
        [eponai.common.ui.elements.css :as css]))

(defn get-contents [editor]
  #?(:cljs
     (js/JSON.stringify (.getContents editor))))

(defn sanitize-html [dirty-html]
  (when (not-empty dirty-html)
    (let [allowed-classes ["ql-align-right" "ql-align-center" "ql-size-small" "ql-size-large"]
          allowed-tags ["p" "span" "strong" "ul" "ol" "li" "u" "s" "i" "em" "br"]]

      #?(:cljs (let [policy #js {:ALLOWED_TAGS (into-array allowed-tags)}]
                 (.sanitize js/DOMPurify dirty-html policy))
         :clj  (let [policy (a/html-policy :allow-common-inline-formatting-elements
                                           :allow-common-block-elements
                                           :allow-attributes ["class"
                                                              :globally
                                                              :matching [(fn [_ _ value]
                                                                           (some #(when (clojure.string/includes? value %) %)
                                                                                 allowed-classes))]])]
                 (a/html-sanitize policy dirty-html))))))

(defn get-HTML #?@(:cljs [[^js/Quill editor]
                          (when editor
                            (sanitize-html (.. editor -root -innerHTML)))]
                   :clj  [[editor] nil]))

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

(defn set-content [editor content]
  (when editor
    (.. editor -clipboard (dangerouslyPasteHTML (sanitize-html content)))))

(defui QuillEditor
  Object
  (componentDidMount [this]
    (let [{:keys [on-editor-created on-text-change]} (om/get-computed this)
          {:keys [content placeholder theme id enable?]} (om/props this)
          read-only (if (some? enable?) (not enable?) false)]
      #?(:cljs
         (let [element (.getElementById js/document (str id "-quill-editor"))
               editor (js/Quill. element (clj->js
                                           {:theme       (or theme "snow")
                                            :modules     {:toolbar (toolbar-opts)}
                                            :readOnly    read-only
                                            :placeholder placeholder}))
               ;parchment (js/Quill.import "parchment")
               ;quill-style (.. parchment -Attributor -Style)
               ]
           ;(js/Quill.register (quill-style "size", "font-size", #js {:scope (.. parchment -Scope -INLINE)}), true)
           ;(.setContents editor (js/JSON.parse content))
           (.. editor -clipboard (dangerouslyPasteHTML (sanitize-html content)))
           (when on-editor-created
             (on-editor-created editor))
           (when on-text-change
             (.on editor "text-change" (fn [_ _ _]
                                         (on-text-change editor))))
           (om/update-state! this assoc :editor editor)))))
  (componentDidUpdate [this prev-state prev-props]
    (let [{:keys [editor]} (om/get-state this)
          {:keys [enable?]} (om/props this)]
      (when editor
        (.enable editor enable?))))

  (render [this]
    (let [{:keys [id classes enable?]} (om/props this)]
      (my-dom/div
        (cond->> (css/add-class "sl-quill-editor-container rich-text-input" {:id      (str id "-quill-editor-container")
                                                                             :classes classes})
                 (not enable?)
                 (css/add-class "disabled"))
        (dom/div #js {:id (str id "-quill-editor") :className "sl-quill-editor"})))))

(def ->QuillEditor (om/factory QuillEditor))

(defui QuillRenderer
  Object
  (render [this]
    (let [{:keys [html placeholder classes]} (om/props this)]
      (my-dom/div
        (->> {:placeholder             placeholder
              :classes                 (conj classes :sl-quill-renderer)
              :dangerouslySetInnerHTML #js {:__html (sanitize-html html)}}
             (css/add-class "ql-editor"))))))

(def ->QuillRenderer (om/factory QuillRenderer))
