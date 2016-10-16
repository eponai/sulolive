(ns eponai.common.diff
  (:require [clojure.data :as data]
            [clojure.set :as set]))

(declare recursive-diff-merge)

(defn diff [a b]
  (data/diff a b))

(defn undiff
  "returns the state of x after reversing the changes described by a diff against
   an earlier state (where before and after are the first two elements of the diff)"
  [x [before after]]
  (let [[a _ _] (data/diff x after)]
    (recursive-diff-merge a before)))

;; Giving other names to diff and undiff, making it easier to think about.
;; Also places the 'to' parameter in the same position.

(defn apply-diff
  "same as undiff, but with switched arguments"
  [diff to]
  (undiff to diff))

;; Inspiration from: https://clojuredocs.org/clojure.data/diff#example-56c7039fe4b0b41f39d96cce
;; To invert a  diff  you can re-apply diff to its output and then merge this back with the prior state
;; This works in almost all cases (with the exception of preserving empty maps)

(defn- seqzip
  "returns a sequence of [[value-left value-right]....]"
  [a b]
  (lazy-seq
    (when (or (seq a) (seq b))
      (cons [(first a) (first b)]
            (seqzip (rest a) (rest b))))))

(defn- recursive-diff-merge
  "Merge two structures recusively, taking non-nil values from sequences and maps and merging sets"
  [part-state original-state]
  (cond
    (sequential? part-state)
    (map (fn [[left right]] (recursive-diff-merge left right))
         (seqzip part-state original-state))
    (map? part-state)
    (merge-with recursive-diff-merge part-state original-state)
    (set? part-state)
    (set/union part-state original-state)
    (nil? part-state )
    original-state
    :else
    part-state))
