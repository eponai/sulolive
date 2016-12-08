(ns eponai.common
  "Namespace for common reader conditional code"
  #?(:clj (:refer-clojure :exclude [format]))
  #?(:cljs (:require
             [cljs.reader]
             [goog.string :as gstring])))

(defn parse-long [l]
  #?(:clj  (Long/parseLong l)
     :cljs (cljs.reader/read-string l)))

(defn format-str [s & args]
  #?(:clj  (apply clojure.core/format s args)
     :cljs (apply gstring/format s args)))