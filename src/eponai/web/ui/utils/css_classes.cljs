(ns eponai.web.ui.utils.css-classes
  (:require
    [clojure.string :as s]))

(def all-classnames
  {
   ; Float classes
   ::float-right "float-right"
   ::float-left  "float-left"
   ; Add clearfix class to parent of floating elements to make sure sizing is made correctly.
   ::clearfix    "clearfix"

   ; Base button class
   ::button      "button"

   ; Should button just be outlined (default not hollow)
   ::hollow      "hollow"

   ; Sizes (default medium)
   ::tiny        "tiny"
   ::small       "small"
   ::medium      "medium"
   ::large       "large"

   ; Colors (default primary)
   ::primary     "primary"
   ::secondary   "secondary"
   ::success     "success"
   ::alert       "alert"
   ::warning     "warning"
   ::black       "black"

   ; Flex grid
   ::row         "row"
   ::column      "column"
   })

(defn classes
  "Create an  array of class keywords given the functions in the input.
  fs is an array of functions where each f takes a collection of class keywords and returns the modified collection."
  [& fs]
  (let [all-classes-fn (apply comp fs)]
    (all-classes-fn)))

(defn class-names
  ([classes]
    (class-names classes ""))
  ([classes class-str]
   (let [classname (fn [ck]
                     (let [cn (get all-classnames ck)]
                       (or cn (name ck))))]
     (s/join " " (conj (map classname classes) class-str)))))

(defn create [element-fn options & children]
  (element-fn
    options
    children))

(defn add-class [options ck]
  (update options :classes conj ck))

; ###########Floats ##################
(defn float-right [f]
  (fn [options & children]
    (f (add-class options ::float-right) children)))

(defn float-left [& [classes]]
  (conj classes ::float-left))

(defn clearfix [& [classes]]
  (conj classes ::clearfix))


; ######### Colors ##############

(defn success [& [classes]]
  (conj classes ::success))

(defn small [& [classes]]
  (conj classes ::small))

(defn black [& [classes]]
  (conj classes ::black))

(defn hollow [& [classes]]
  (conj classes ::hollow))



; ############# Flex Grid ########################

(defn flex-row [& [classes]]
  (conj classes ::row))

(defn flex-column [& [classes]]
  (conj classes ::column))
