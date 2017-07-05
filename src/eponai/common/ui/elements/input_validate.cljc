(ns eponai.common.ui.elements.input-validate
  (:require
    #?(:cljs [cljs.spec.alpha :as s]
       :clj [clojure.spec.alpha :as s])
            [eponai.common.ui.dom :as my-dom]
            [eponai.common.ui.elements.css :as css]
            [taoensso.timbre :refer [debug]]))

(defn is-invalid-path? [id-key error]
  (if error
    (let [problems (::s/problems (:explain-data error))
          invalid-paths (map (fn [p] (some #(= % id-key) p))
                             (map :path problems))]
      (debug "Invalid paths: " invalid-paths)
      (some true? invalid-paths))
    false))

(defn select
  [opts error & children]
  (let [is-invalid-input? (some #(= % (:id opts)) (:invalid-paths error))]
    (my-dom/select
      (cond->> opts
               is-invalid-input?
               (css/add-class :is-invalid-input))
      children)))

(defn input
  ([opts error]
   (let [is-invalid-input? (some #(= % (:id opts)) (:invalid-paths error))]
     (my-dom/input
       (cond->> opts
                is-invalid-input?
                (css/add-class :is-invalid-input)))))

  ([id-key opts error]
   (let [is-invalid-input? (is-invalid-path? id-key error)]
     (my-dom/input
       (cond->> opts
                is-invalid-input?
                (css/add-class :is-invalid-input))))))

(defn validate
  [spec m form-inputs & [prefix-key]]
  (when-let [err (s/explain-data spec m)]
    (let [problems (::s/problems err)
          invalid-paths (map (fn [p]
                               (str prefix-key (get form-inputs (last p))))
                             (into (map :path problems) (map :via problems)))]
      {:explain-data  err
       :invalid-paths (set invalid-paths)})))
