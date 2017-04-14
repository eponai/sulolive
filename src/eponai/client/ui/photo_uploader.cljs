(ns eponai.client.ui.photo-uploader
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [chan alts! put!]]
    [cljs.core]
    [cljs.spec :as s]
    [goog.events :as events]
    [goog.crypt]
    [goog.crypt.Sha256]
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

(s/def ::photo-size #(>= 5000000 %))
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

(defn hashed [eid]
  (let [digest (fn [hasher bytes]
                 (.update hasher bytes)
                 (.digest hasher))
        str->bytes (fn [s]
                     (goog.crypt/stringToUtf8ByteArray s))]
    (goog.crypt/byteArrayToHex (digest (goog.crypt.Sha256.) (str->bytes (str eid))))))

(defn queue-file [e owner {:keys [upload-queue]}]
  (let [file (first (array-seq (.. e -target -files)))
        {:keys [on-photo-queue]} (om/get-computed owner)
        validation (s/explain-data ::photo-size (.-size file))]
    (debug {:original file})
    (when (nil? validation)
      (put! upload-queue {:file     file
                          :metadata {:x-amz-meta-size (.-size file)}}))
    (when on-photo-queue
      ;reader.onloadend = function() {
      ;                               img.src = reader.result;
      ;                               }
      ;reader.readAsDataURL(file);
      (let [reader (js/FileReader. )]
        (set! (.-onloadend reader) (fn []
                                     (on-photo-queue (.-result reader))))
        (.readAsDataURL reader file)))
    (om/update-state! owner assoc :loading? (nil? validation) :validation validation)))

(defui PhotoUploader
  static om/IQuery
  (query [_]
    [{:query/auth [:user/email]}])
  Object
  (s3-key [this filename]
    (let [{:keys [query/auth]} (om/props this)]
      (let [sha-dbid (hashed (:db/id auth))
            chars 2
            level1 (clojure.string/join (take chars sha-dbid))
            level2 (clojure.string/join (take chars (drop chars sha-dbid)))]
        (str "photos/temp/" level1 "/" level2 "/" sha-dbid "/" filename))))

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
        (let [{:keys [dropped-queue upload-queue uploaded uploads]} (om/get-state this)
              {:keys [on-photo-upload]} (om/get-computed this)]
          (let [[v ch] (alts! [uploaded])]
            (cond
              ;(= ch dropped-queue)
              ;(put! upload-queue v)
              (= ch uploaded)
              (do
                (when on-photo-upload
                  (on-photo-upload  (:response v)))
                (om/update-state! this (fn [st] (-> st
                                                    (assoc :loading? false)
                                                    (update :uploads conj v)))))))))))

  (componentDidUpdate [this prev-props prev-state]
    (let [new-state (om/get-state this)
          {:keys [on-change]} (om/get-computed this)]
      (debug "Photo uploader updated: " (om/get-state this))
      (when (not= (:uploads prev-state) (:uploads new-state))
        (when on-change
          (on-change (:uploads new-state))))))

  (render [this]
    (let [{:query/keys [auth]} (om/props this)
          {:keys [loading? validation] :as state} (om/get-state this)
          {:keys [id hide-label?]} (om/get-computed this)]
      (dom/div
        #js {:className "photo-uploader text-center"}

        ;(if loading?
        ;  (dom/i #js {:className "fa fa-spinner fa-spin fa-2x"}))
        (dom/p nil (dom/label #js {:className (if (nil? validation) "hide" "is-invalid-label")}
                              "Sorry, your photo is too large. Please select a photo of max 5MB."))
        (when-not hide-label?
          (dom/label #js {:htmlFor (str "file-" id) :className "button hollow expanded"}
                     (if loading?
                       (dom/i #js {:className "fa fa-spinner fa-spin fa-2x"})
                       "Upload Photo")))
        ;(apply dom/ul nil (map (fn [{:keys [file response] :as upload}]
        ;                         (debug "Upload: " upload)
        ;                         (dom/li nil
        ;                                 (dom/img #js {:src (:location response)}) (.-name file)))  (:uploads state)))
        (dom/input #js {:type      "file"
                        :value     ""
                        :name      "file"
                        :id        (str "file-" id)
                        :accept    "image/jpeg"
                        :onChange  #(when-not loading? (queue-file % this state))
                        :className "show-for-sr"})))))

(def ->PhotoUploader (om/factory PhotoUploader {:key-fn #(str "photo-uploader")}))