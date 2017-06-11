(ns eponai.common
  "Namespace for common reader conditional code"
  #?(:clj
     (:refer-clojure :exclude [format]))
  (:require
    [clojure.string :as string]
    [clojure.pprint :refer [cl-format]]
    [taoensso.timbre :refer [debug]]

    #?(:cljs [cljs.reader])
    #?(:cljs [goog.string :as gstring])))

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