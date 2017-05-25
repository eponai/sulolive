(ns eponai.client.ui.photo-uploader
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [chan alts! put!]]
    [om.next :as om :refer [defui]]
    [cljs.core.async :as async]
    [eponai.web.modules :as modules]
    [eponai.common.photos :as photos]
    [eponai.common.ui.dom :as my-dom]
    [taoensso.timbre :refer [error debug]])
  (:import [goog.events EventType]))

(defn queue-file [component ^js/Event e]
  (let [^js/File file (first (array-seq (.. e -target -files)))
        {:keys [upload-queue]} (om/get-state component)
        {:keys [on-photo-queue]} (om/get-computed component)]
    (debug {:original file})
    (let [reader (js/FileReader.)]
      (set! (.-onloadend reader) (fn []
                                   (put! upload-queue {:file (.-result reader)})
                                   (when on-photo-queue
                                     (on-photo-queue (.-result reader)))))
      (.readAsDataURL reader file))))

(defn photo-upload-pipe [uploaded preset]
  (let [queue (chan)]
    (async/pipeline-async
      3
      uploaded
      (fn [{:keys [file]} c]
        (async/take! (photos/upload-photo file preset)
                     (fn [response]
                       (let [upload-data (:body response)]
                         (debug "Cloudinary upload data: " upload-data)
                         (async/put! uploaded upload-data)
                         (async/close! c)))))
      queue)
    queue))

(defui PhotoUploader
  Object
  (initLocalState [this]
    (let [uploaded (chan 20)
          {:keys [preset]} (om/get-computed this)]
      {:upload-queue (photo-upload-pipe uploaded preset)
       :uploaded     uploaded
       :uploads      []}))
  (componentDidMount [this]
    (go
      (while true
        (let [{:keys [upload-queue uploaded]} (om/get-state this)
              {:keys [on-photo-upload]} (om/get-computed this)]
          (let [[response ch] (alts! [uploaded upload-queue])]
            (cond
              (= ch uploaded)
              (when on-photo-upload
                (on-photo-upload response))))))))

  (componentDidUpdate [this _ _]
    (debug "Photo uploader updated: " (om/get-state this)))

  (render [this]
    (let [{:keys [id]} (om/props this)]
      (my-dom/div
        {:classes ["photo-uploader text-center"]}
        (my-dom/input {:type      "file"
                       :value     ""
                       :name      "file"
                       :id        (or id "file-uploader")
                       :accept    "image/*"
                       :onChange  #(queue-file this %)
                       :className "show-for-sr"})))))

(def ->PhotoUploader (om/factory PhotoUploader))

(modules/set-loaded! :photo-uploader)