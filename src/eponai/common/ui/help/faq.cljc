(ns eponai.common.ui.help.faq
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]))

(defui FAQ
  Object
  (render [this]
    (dom/div
      nil
      (dom/h1 nil "FAQ")
      (dom/h2 nil "Stores")
      (dom/section
        nil
        (dom/h3 nil "What does \"Store URL\" mean?")
        (dom/p nil
               (dom/span nil "This is the link to your store page. When creating your store, it is assigned a unique numeric id which will be used in your store link, like ")
               (dom/strong nil "sulo.live/shop/1234")
               (dom/span nil ". However, if you want to change your link to something that is easier to remember for your customers, you can change your Store URL to something more descriptive of your brand."))
        (dom/p nil
               (dom/span nil "For example, if I create a store called \"My Store\", rather than using the default link ")
               (dom/strong nil "sulo.live/shop/4321")
               (dom/span nil " I want to change it to ")
               (dom/strong nil "sulo.live/shop/mystore")
               (dom/span nil " which looks nicer and is easier to remember for my customers. To do that I just change the Store URL and enter \"mystore\".")))

      (dom/section
        nil
        (dom/h3 nil "What sizes should I use on photo uploads?")
        (dom/p nil "We have the following limits in place for photo uploads:")
        (dom/ul nil
                (dom/li nil (dom/p nil (dom/span nil "Cover photos: ") (dom/strong nil "2048px")))
                (dom/li nil (dom/p nil (dom/span nil "Other photos: ") (dom/strong nil "1024px"))))
        (dom/p nil "All photos larger than the above measurements will be automatically scaled down, so if you upload photos larger than the above you should be safe from blur."))


      (dom/section
        nil
        (dom/h3 nil "How will the customer receipts look?")
        (dom/p nil (dom/span nil "Customers will receive receipts in their email, and also have all their receipts available on SULO Live. We will include the following information in their receipts:"))
        (dom/ul nil
                (dom/li nil (dom/p nil
                                   (dom/strong nil "Contact email")
                                   (dom/span nil " - to let customers contact you about their order. Change your contact email in the Business section.")))
                (dom/li nil (dom/p nil (dom/strong nil "Store name and profile photo")
                                   (dom/span nil " - to clearly show your brand on the receipts and remind the customers where they made their purchase. Update your info in the Store info section.")))
                (dom/li nil (dom/p nil
                                   (dom/strong nil "Store URL")
                                   (dom/span nil " - so the customer can easily find you again. Change your Store URL in the Store info section."))))))))

(def ->FAQ (om/factory FAQ))