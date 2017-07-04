(ns eponai.web.ui.pagination
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [om.next :as om :refer [defui]]))

;; How many pages in front of and after the current page in the paginator.
(def buffer 2)

(defn page->item [this page]
  (let [{:keys [current-page page->anchor-opts]} (om/props this)]
    (if (= current-page page)
      (dom/li (css/add-class :current)
              (dom/span (css/add-class :show-for-sr)
                        "You're on page")
              (dom/span nil page))
      (dom/li nil (dom/a (page->anchor-opts page)
                         (dom/span nil page))))))

(defui Pagination
  Object
  (render [this]
    (let [{:keys [current-page pages page->anchor-opts]} (om/props this)]
      (let [first-page? (zero? current-page)
            last-page? (= current-page (dec (count pages)))
            pages (into [] pages)

            first-number (first pages)
            ;; Using (peek ..) instead of (last ..) as it is O(1) for vectors to get the last element.
            ;; (last ..) is O(n). So make sure that vectors flow through this code.
            last-number (peek pages)

            negative-numbers (range (- current-page buffer) (inc 0))
            out-of-bound-numbers (range (inc last-number) (inc (+ current-page buffer)))

            ;; First create a range around the current page
            ;; Example current-page 5, buffer 2: [3 4 5 6 7]
            ;; Example current-page 0, buffer 2: [0 1 2]
            numbers (vec (range (max first-number (- current-page buffer))
                                (min last-number (inc (+ current-page buffer)))))
            ;; If there are any negative numbers add them to the end of the range
            ;; Example current page 0, buffer 2: [0 1 2 3 4]
            numbers (cond-> numbers
                            (seq negative-numbers)
                            (into (->> (range (inc (peek numbers)) (+ (inc (peek numbers)) (count negative-numbers))))))
            ;; Do the same for out-of-bound numbers.
            numbers (cond->> numbers
                             (seq out-of-bound-numbers)
                             (into (vec (range (- (first numbers) (count out-of-bound-numbers)) (first numbers)))))
            ;; Watch out for the bounds now that we've added stuff.
            numbers (into []
                          (comp (drop-while #(< % first-number))
                                (take-while #(<= % last-number)))
                          numbers)

            ;; If there's more than 1 step between the first number and the first number in pages
            ;; there's a left-ellipse. Similarly for the right-ellipse.
            left-ellipse? (< 1 (- (first numbers) first-number))
            right-ellipse? (< 1 (- last-number (peek numbers)))

            elips-item (dom/li (css/add-class :ellipsis {:aria-hidden true}))
            ;; Bringing it all together.
            number-items (->> (concat [(page->item this first-number)]
                                      [(when left-ellipse? elips-item)]
                                      (map #(page->item this %) (cond->> numbers
                                                                         ;; Don't include the first number again.
                                                                         (= (first numbers) first-number) rest
                                                                         (= (peek numbers) last-number) butlast))
                                      [(when right-ellipse? elips-item)]
                                      [(page->item this last-number)])
                              (filter some?))]
        ;; Finally render.
        (dom/ul
          (->> {:role       "navigation"
                :aria-label "Product page navigation"}
               (css/add-class :pagination)
               (css/add-class :text-center))
          (dom/li (cond->> (css/add-class :pagination-previous)
                           first-page?
                           (css/add-class :disabled))
                  (cond->> [(dom/span nil "Previous")
                            (dom/span (css/add-class :show-for-sr) "page")]
                           (not first-page?)
                           (dom/a (page->anchor-opts (dec current-page)))))
          number-items
          (dom/li (cond->> (css/add-class :pagination-next)
                           last-page?
                           (css/add-class :disabled))
                  (cond->> [(dom/span nil "Next")
                            (dom/span (css/add-class :show-for-sr) "page")]
                           (not last-page?)
                           (dom/a (page->anchor-opts (inc current-page))))))))))

(def ->Pagination (om/factory Pagination))
