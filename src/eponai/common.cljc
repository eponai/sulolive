(ns eponai.common
  "Namespace for common reader conditional code"
  #?(:clj
     (:refer-clojure :exclude [format]))
  (:require
    [clojure.string :as string]
    [clojure.pprint :refer [cl-format]]
    [taoensso.timbre :refer [debug]]
    [cognitect.transit :as transit]

    #?(:cljs [cljs.reader])
    #?(:cljs [goog.string :as gstring]))
  #?(:clj
     (:import (java.io ByteArrayInputStream ByteArrayOutputStream))))

(defn parse-long [l]
  (if (and (number? l) (= l (long l)))
    l
    #?(:clj  (Long/parseLong l)
       :cljs (cljs.reader/read-string l))))

(defn parse-long-safe [l]
  (when (some? l)
    (if (and (number? l) (= l (long l)))
      l
      (try #?(:clj  (Long/parseLong l)
              :cljs (cljs.reader/read-string l))
           (catch #?@(:clj [Exception e] :cljs [:default e])
                  nil)))))

(defn format-str [s & args]
  #?(:clj  (apply clojure.core/format s args)
     :cljs (apply gstring/format s args)))

(defn price->str [n & [currency]]
  (let [currency-symbol "$"
        decimals (or (second (string/split (str n) #"\.")) "")
        decimal-long (or (parse-long-safe (subs decimals 0 2)) 0)]
    (str (cl-format nil "$~:D" (int n))
         (cl-format nil ".~2'0D" (int decimal-long)))))

(defn ordinal-number [n]
  (let [formatted-str (cl-format nil "~:r" n)
        length (count formatted-str)]
    (str n
         (subs formatted-str (- length 2) length))))

(defn two-decimal-percent [rate]
  (str (format-str "%.2f" (* 100 (double (or rate 0)))) "%"))

(defn substring
  ([s start]
   (substring s start nil))
  ([s start end]
   (let [length (count s)
         st (-> start (min length) (max 0))
         en (if end (-> end (min length) (max 0)) length)]
     (subs (or s "") st en))))


;; ########## TRANSIT ##############
(defn read-transit [input & [format]]
  #?(:cljs
          (let [reader (transit/reader (or format :json))]
            (transit/read reader input))
     :clj (let [in (ByteArrayInputStream. (.getBytes input))
                reader (transit/reader in (or format :json))]
            (transit/read reader))))

(defn write-transit [input & [format size]]
  #?(:cljs
          (let [writer (transit/writer (or format :json))]
            (transit/write writer input))
     :clj (let [out (ByteArrayOutputStream. (or size 1024))
                writer (transit/writer out (or format :json))]
            (transit/write writer input)
            (str out))))