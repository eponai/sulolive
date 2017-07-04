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
     (:import
       [goog.events EventType]
       [goog.crypt Sha256])))

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

(defn hashed [eid]
  #?(:cljs (let [digest (fn [hasher bytes]
                          (.update hasher bytes)
                          (.digest hasher))
                 str->bytes (fn [s]
                              (goog.crypt/stringToUtf8ByteArray s))]
             (goog.crypt/byteArrayToHex (digest (goog.crypt.Sha256.) (str->bytes (str eid)))))))

(defui FileUploader
  Object
  (s3-key [this filename]
    (let [{:keys [owner]} (om/get-computed this)]
      (let [sha-dbid (hashed (:db/id owner))
            chars 2
            level1 (clojure.string/join (take chars sha-dbid))
            level2 (clojure.string/join (take chars (drop chars sha-dbid)))
            ]
        (debug "Owner: " owner)
        (if (:db/id owner)
          (str "id-docs/temp/" level1 "/" level2 "/" (:db/id owner) "/" filename)
          (error "Trying to create S3 key without a :db/id for the document owner. Make sure you pass in an entity that owns this document (i.e. a store or user)")))))
  (initLocalState [this]
    #?(:cljs
       (let [uploaded (chan 20)]
         {:dropped-queue (chan 20)
          :upload-queue  (s3/s3-pipe uploaded {:server-url "/aws" :key-fn #(.s3-key this %)})
          :uploaded      uploaded
          :uploads       []})))

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