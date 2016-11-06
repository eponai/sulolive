(ns eponai.web.ui.elements.css-classes)

(def all-classnames
  {
   ::float-right "float-right"
   ; Base button class
   ::button    "button"

   ; Should button just be outlined (default not hollow)
   ::hollow    "hollow"

   ; Sizes (default medium)
   ::tiny      "tiny"
   ::small     "small"
   ::medium    "medium"
   ::large     "large"

   ; Colors (default primary)
   ::primary   "primary"
   ::secondary "secondary"
   ::success   "success"
   ::alert     "alert"
   ::warning   "warning"
   ::black     "black"})

(defn classes
  "Create an array of class keywords given the functions in the input.
  fs is an array of functions where each f takes a collection of class keywords and returns the modified collection."
  [& fs]
  (let [all-classes-fn (apply comp fs)]
    (all-classes-fn)))

(defn float-right [& [classes]]
  (conj classes ::float-right))

(defn success [& [classes]]
  (conj classes ::success))

(defn small [& [classes]]
  (conj classes ::small))

(defn black [& [classes]]
  (conj classes ::black))

(defn hollow [& [classes]]
  (conj classes ::hollow))
