(ns eponai.common
  "Namespace for common reader conditional code"
  #?(:clj (:refer-clojure :exclude [format]))
  #?(:cljs (:require
             [cljs.reader]
             [goog.string :as gstring])))

(defn parse-long [l]
  (if (and (number? l) (= l (long l)))
    l
    #?(:clj  (Long/parseLong l)
       :cljs (cljs.reader/read-string l))))

(defn parse-long-safe [l]
  (if (and (number? l) (= l (long l)))
    l
    (try #?(:clj  (Long/parseLong l)
            :cljs (cljs.reader/read-string l))
      (catch #?@(:clj [Exception e] :cljs [:default e])
             nil))))

(defn format-str [s & args]
  #?(:clj  (apply clojure.core/format s args)
     :cljs (apply gstring/format s args)))