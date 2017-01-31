(ns eponai.client.ui.photo-uploader
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [chan alts! put!]]
    [cljs.core]
    [goog.events :as events]
    [s3-beam.client :as s3]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.client.auth :as auth])
  (:import [goog.events EventType]))

;(defn resize-image [img width height]
;  (let [canvas (doto (.createElement js/document "canvas")
;                 (aset "width" width)
;                 (aset "height" height))
;        ctx  (.getContext canvas "2d")]
;    (.drawImage ctx img 0 0 width height)
;    canvas))

(defn filelist-files [filelist]
  (let [filelist (.-files filelist)]
    (vec (for [k (js-keys filelist)
               :let [value (aget filelist k)]
               :when (identical? (type value) js/File)]
           value))))

(defn event-files
  "Takes a goog event and returns the files
  TODO: also get jquery events working"
  [event]
  (filelist-files (-> event .getBrowserEvent .-dataTransfer)))

(defn listen-file-drop
  ([el] (listen-file-drop el (chan)))
  ([el out & {:keys [concat]
              :or {concat true}}]
   ;(let [handler (events/FileDropHandler. el true)])
   ;(events/listen el EventType.DROP
   ;               (fn [e] (let [files (event-files e)]
   ;                         (.log js/console "dropped")
   ;                         (.log js/console (-> e .getBrowserEvent .-dataTransfer .-files prim-seq))
   ;                         (if concat
   ;                           (doseq [f files] (put! out f))
   ;                           (put! out files)))))
   out))

(defn s3-key [filename]
  )

(defn queue-file [e owner {:keys [upload-queue]}]
  (let [file (first (array-seq (.. e -target -files)))
        ;resized (resize-image file 100 100)
        ]
    (debug {:original file})
    (put! upload-queue {:file     file
                        :metadata {:x-amz-meta-size (.-size file)}})
    (om/update-state! owner assoc :loading? true)))

(defui PhotoUploader
  Object
  (s3-key [this filename]
    (let [{:keys [auth]} (om/get-computed this)]
      (str "photos/temp/" (:db/id auth) "/" filename)))
  (initLocalState [this]
    (let [uploaded (chan 20)]
      {:dropped-queue (chan 20)
       :upload-queue  (s3/s3-pipe uploaded {:server-url "/aws" :key-fn #(.s3-key this %)})
       :uploaded      uploaded
       :uploads       []}))
  (componentDidMount [this]
    ;(listen-file-drop js/document (om/get-state this :dropped-queue))
    (go
      (while true
        (let [{:keys [dropped-queue upload-queue uploaded uploads]} (om/get-state this)]
          (let [[v ch] (alts! [uploaded])]
            (cond
              ;(= ch dropped-queue)
              ;(put! upload-queue v)
              (= ch uploaded)
              (do
                (om/transact! this `[(photo/upload ~{:photo-info (:response v)})])
                (om/update-state! this update :uploads conj v))))))))

  (componentDidUpdate [this prev-props prev-state]
    (let [new-state (om/get-state this)
          {:keys [on-change]} (om/get-computed this)]
      (debug "Photo uploader updated: " (om/get-state this))
      (when (not= (:uploads prev-state) (:uploads new-state))
        (when on-change
          (on-change (:uploads new-state))))))

  (render [this]
    (let [{:keys [auth]} (om/get-computed this)
          {:keys [loading?] :as state} (om/get-state this)]
      (debug "Authenticated user: " auth)
      (dom/div
        #js {:className "photo-uploader text-center"}

        ;(if loading?
        ;  (dom/i #js {:className "fa fa-spinner fa-spin fa-2x"}))
        (dom/label #js {:htmlFor "file" :className "button hollow expanded"}
                   (if loading?
                     (dom/i #js {:className "fa fa-spinner fa-spin fa-2x"})
                     "Upload File"))
        ;(apply dom/ul nil (map (fn [{:keys [file response] :as upload}]
        ;                         (debug "Upload: " upload)
        ;                         (dom/li nil
        ;                                 (dom/img #js {:src (:location response)}) (.-name file)))  (:uploads state)))
        (dom/input #js {:type      "file"
                        :value     ""
                        :name      "file"
                        :id        "file"
                        :accept    "image/jpeg"
                        :onChange  #(when-not loading? (queue-file % this state))
                        :className "show-for-sr"})))))

(def ->PhotoUploader (om/factory PhotoUploader {:key-fn #(str "photo-uploader")}))