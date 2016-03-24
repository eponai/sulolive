(ns eponai.mobile.components.listview-datasource
  (:require [taoensso.timbre :refer-macros [debug]]))

(defn wrap-api-with-debug [data-source]
  {:pre [(map? data-source)]}
  (reduce-kv (fn [m k v]
               (if (fn? v)
                 (assoc m k (fn [& args]
                              (debug "Calling fn: " k " with args: " args)
                              (let [ret (apply v args)]
                                (debug "returned for: " k " ret: " ret)
                                ret)))
                 (assoc m k v)))
             {}
             data-source))

(defn data-source-sectionless [prev-data data]
  {:pre [(or (nil? prev-data)
             (implements? IIndexed prev-data))
         (or (nil? data)
             (implements? IIndexed data))]}
  (let [rows (if (seq data)
               #js [(into-array data)]
               #js [])]
    (clj->js
     ;; row and sectionIdentities are needed because
     ;; React.ListView uses them, even though they are
     ;; private variables. Nice.
     {:rowIdentities             rows
      :sectionIdentities         #js []

      ;; React.ListView.DataSource Public API:
      :getRowCount               (fn []                     ;; number
                                   (count data))
      :getRowAndSectionCount     (fn []                     ;; number
                                   (count data))
      :rowShouldUpdate           (fn [section-index row-index] ;; bool
                                   (not= (nth data row-index)
                                         (nth prev-data row-index nil)))
      :getRowData                (fn [section-index row-index] ;; any
                                   (nth data row-index))
      :getRowIDForFlatIndex      (fn [index]                ;; maybe string
                                   (when (< index (count data))
                                     index))
      :getSectionIDForFlatIndex  (fn [index]                ;; maybe string
                                   nil)
      :getSectionLengths         (fn []                     ;; (array<num>)
                                   #js [(count data)])
      :sectionHeaderShouldUpdate (fn [section-index]        ;; bool
                                   false)
      :getSectionHeaderData      (fn [section-index]        ;; any
                                   nil)})))


(defn data-source-with-sections [prev-data data]
  {:pre [(or (nil? prev-data)
             (map? prev-data))
         (or (nil? data)
             (and (map? data)
                  (::section-order data)))]}
  (assert (every? (set (::section-order data))
                  (keys (dissoc data ::section-order)))
          (str "Every key was not in the ::section-order key. Data: "
               data))
  (let [[rows sections]
        (reduce (fn [[rows sections] k]
                     [(conj! rows (into-array (get data k)))
                      (conj! sections k)])
                   [(transient []) (transient [])]
                   (::section-order data))
        rows (persistent! rows)
        row-count (reduce + 0 (map count rows))
        sections (persistent! sections)
        sect-idx->key (fn [section-index]
                        (get sections section-index))]
    (clj->js
      ;; row and sectionIdentities are needed because
      ;; React.ListView uses them, even though they are
      ;; private variables. Nice.
      {:rowIdentities             (into-array rows)
       :sectionIdentities         (into-array sections)

       ;; React.ListView.DataSource Public API:
       :getRowCount               (fn []                    ;; number
                                    row-count)
       :getRowAndSectionCount     (fn []                    ;; number
                                    (+ (count (keys data)) row-count))
       :rowShouldUpdate           (fn [section-index row-index] ;; bool
                                    (not= (-> data
                                              (get (sect-idx->key section-index))
                                              (nth row-index))
                                          (some-> prev-data
                                                  (get (sect-idx->key section-index) nil)
                                                  (nth row-index nil))))
       :getRowData                (fn [section-index row-index] ;; any
                                    (-> data
                                        (get (sect-idx->key section-index))
                                        (nth row-index)))
       :getRowIDForFlatIndex      (fn [index]               ;; maybe string
                                    (reduce
                                      (fn [idx row]
                                        (let [c (count row)]
                                          (if (>= idx c)
                                            (- idx c)
                                            (nth row idx))))
                                      index
                                      rows))
       :getSectionIDForFlatIndex  (fn [index]               ;; maybe string
                                    (reduce
                                      (fn [idx [sect row]]
                                        (let [c (count row)]
                                          (if (>= idx c)
                                            (- idx c)
                                            (nth sect idx))))
                                      index
                                      (map vector sections rows)))
       :getSectionLengths         (fn []                    ;; (array<num>)
                                    (into-array (map count rows)))
       :sectionHeaderShouldUpdate (fn [section-index]       ;; bool
                                    (not= (get data section-index)))
       :getSectionHeaderData      (fn [section-index]       ;; any
                                    (get data section-index))})))
