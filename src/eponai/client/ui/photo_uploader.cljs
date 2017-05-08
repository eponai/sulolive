(ns eponai.client.ui.photo-uploader
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [chan alts! put!]]
    [cljs.core]
    [goog.crypt]
    [goog.crypt.Sha256]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    ;[clojure.core.async :as async]
    [cljs.core.async :as async]
    [cljs-http.client :as http]
    [eponai.web.modules :as modules])
  (:import [goog.events EventType]))

;(defn hashed [eid]
;  (let [digest (fn [^js/goog.crypt.Sha256 hasher bytes]
;                 (.update hasher bytes)
;                 (.digest hasher))
;        str->bytes (fn [s]
;                     (goog.crypt/stringToUtf8ByteArray s))]
;    (goog.crypt/byteArrayToHex (digest (goog.crypt.Sha256.) (str->bytes (str eid))))))

(defn queue-file [^js/Event e owner {:keys [upload-queue]}]
  (let [^js/File file (first (array-seq (.. e -target -files)))
        {:keys [on-photo-queue]} (om/get-computed owner)
        ;validation (s/explain-data ::photo-size (.-size file))
        ]
    (debug {:original file})
    ;(when (nil? validation)
    ;  (put! upload-queue {:file     file
    ;                      :metadata {:x-amz-meta-size (.-size file)}}))
    ;(when on-photo-queue
    ;reader.onloadend = function() {
    ;                               img.src = reader.result;
    ;                               }
    ;reader.readAsDataURL(file);
    (let [reader (js/FileReader.)]
      (set! (.-onloadend reader) (fn []
                                   (put! upload-queue {:file     (.-result reader)
                                                       :metadata {:x-amz-meta-size (.-size file)}})
                                   (when on-photo-queue
                                     (on-photo-queue (.-result reader)))
                                   ))
      (.readAsDataURL reader file)
      ;)
      )
    ;(om/update-state! owner assoc :loading? (nil? validation) :validation validation)
    ))

(defn cloudinary-endpoint [endpoint]
  (let [api "https://api.cloudinary.com/v1_1"
        cloud-name "sulolive"
        resource-type "image"]
    (clojure.string/join "/" [api cloud-name resource-type endpoint])))

(defn cloudinary-pipe [uploaded]
  (let [queue (chan)]
    (async/pipeline-async
      3
      uploaded
      (fn [{:keys [file]} c]
        (async/take! (http/post (cloudinary-endpoint "upload") {:form-params       {:file          file
                                                                                    :upload_preset "product-photo"}
                                                                :headers           {"X-Requested-With" "XMLHttpRequest"}
                                                                :with-credentials? false})
                     (fn [response]
                       (let [upload-data (:body response)]
                         (debug "Cloudinary upload data: " upload-data)
                         (async/put! uploaded upload-data)
                         (async/close! c)))))
      queue)
    queue)

  ;(let [opts (merge {:server-url "/sign" :response-parser #(reader/read-string %)}
  ;                  opts)
  ;      to-process (chan)
  ;      signed (chan)]
  ;  (async/pipeline-async 3
  ;                        signed
  ;                        (partial sign-file
  ;                           (:server-url opts)
  ;                           (:response-parser opts)
  ;                           (:key-fn opts)
  ;                           (:headers-fn opts))
  ;                        to-process)
  ;  (async/pipeline-async 3 report-chan upload-file signed)
  ;  to-process)
  )

(defui PhotoUploader
  static om/IQuery
  (query [_]
    [{:query/auth [:user/email]}])
  Object
  ;(s3-key [this filename]
  ;(let [{:keys [query/auth]} (om/props this)]
  ;  (let [sha-dbid (hashed (:db/id auth))
  ;        chars 2
  ;        level1 (clojure.string/join (take chars sha-dbid))
  ;        level2 (clojure.string/join (take chars (drop chars sha-dbid)))]
  ;    (str "photos/temp/" level1 "/" level2 "/" sha-dbid "/" filename)))
  ;)

  (initLocalState [this]
    (let [uploaded (chan 20)]
      {:dropped-queue (chan 20)
       :upload-queue  (cloudinary-pipe uploaded)            ;(s3/s3-pipe uploaded {:server-url "/aws" :key-fn #(.s3-key this %)})
       :uploaded      uploaded
       :uploads       []}))
  (componentDidMount [this]
    ;(listen-file-drop js/document (om/get-state this :dropped-queue))
    (go
      (while true
        (let [{:keys [dropped-queue upload-queue uploaded uploads]} (om/get-state this)
              {:keys [on-photo-upload on-photo-queue]} (om/get-computed this)]
          (let [[response ch] (alts! [uploaded upload-queue])]
            (cond
              (= ch uploaded)
              (do
                (when on-photo-upload
                  (on-photo-upload response))
                (om/update-state! this (fn [st] (-> st
                                                    (assoc :loading? false)
                                                    (update :uploads conj response)))))))))))

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

(modules/set-loaded! :photo-uploader)