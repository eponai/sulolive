(ns eponai.web.s3-uploader
  #?(:cljs
     (:require-macros
       [cljs.core.async.macros :refer [go]]))
  (:require
    #?(:cljs
       [s3-beam.client :as s3])
    #?(:cljs
       [cljs.core.async :refer [chan alts! put!]])
    [om.next :as om :refer [defui]]
    #?(:cljs
       [eponai.web.modules :as modules])
    [eponai.common.ui.dom :as dom]
    [taoensso.timbre :refer [error debug]]
    #?(:cljs
       [cljs.reader :as reader]))
  #?(:cljs
     (:import [goog.events EventType])))

(defn queue-file [component event]
  #?(:cljs
     (let [^js/Event e event
           ^js/File file (first (array-seq (.. e -target -files)))
           {:keys [upload-queue]} (om/get-state component)
           ;{:keys [on-photo-queue]} (om/get-computed component)
           ]
       (put! upload-queue file)
       ;(debug {:original file})
       ;(let [reader (js/FileReader.)]
       ;  (set! (.-onloadend reader) (fn []
       ;                               (put! upload-queue file)))
       ;  (.readAsDataURL reader file))
       )))


(defui FileUploader
  Object
  (initLocalState [this]
    #?(:cljs
       (let [uploaded (chan 20)]
         {:dropped-queue (chan 20)
          :upload-queue  (s3/s3-pipe uploaded {:server-url "/aws" :key-fn (fn [file]
                                                                            (debug "Key for file: " file)
                                                                            (str "temp/" file))})
          :uploaded      uploaded
          :uploads       []})))

  ;(did-mount [_]
  ;  ;(listen-file-drop js/document (om/get-state owner :dropped-queue))
  ;  (go (while true
  ;        (let [{:keys [dropped-queue upload-queue uploaded uploads]} (om/get-state owner)]
  ;          (let [[v ch] (alts! [dropped-queue uploaded])]
  ;            (cond
  ;              (= ch dropped-queue) (put! upload-queue v)
  ;              (= ch uploaded) (om/set-state! owner :uploads (conj uploads v))))))))
  (componentDidMount [this]
    #?(:cljs
       (go (while true
             (let [{:keys [dropped-queue upload-queue uploaded uploads]} (om/get-state this)
                   {:keys [on-upload]} (om/get-computed this)]
               (let [[v ch] (alts! [dropped-queue uploaded])]
                 (cond
                   (= ch dropped-queue) (put! upload-queue v)
                   (= ch uploaded)
                   (do
                     (when on-upload
                       (on-upload v))
                     (om/update-state! this assoc :uploads (conj uploads v))))))))))

  (componentDidUpdate [this _ _]
    (debug "S3 uploader updated: " (om/get-state this)))

  (render [this]
    (let [{:keys [id]} (om/props this)]
      (dom/div
        {:classes ["photo-uploader text-center"]}
        (dom/input {:type      "file"
                    :value     ""
                    :name      "file"
                    :id        (or id "file-uploader")
                    :accept    "image/*"
                    :onChange  #(queue-file this %)
                    :className "show-for-sr"})))))

(def ->FileUploader (om/factory FileUploader))

#?(:cljs
   (modules/set-loaded! :s3-uploader))