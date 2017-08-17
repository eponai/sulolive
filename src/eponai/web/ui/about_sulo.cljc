(ns eponai.web.ui.about-sulo
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as photo]
    [eponai.web.social :as social]))

(defui AboutSulo
  static om/IQuery
  (query [_]
    [{:query/auth [:db/id]}])
  Object
  (render [this]
    (dom/div
      {:id "sulo-about"}
      (dom/div
        (css/add-class :hero)
        (dom/div
          (css/add-class :hero-background)
          (photo/cover {:photo-id "static/about-all"}))
        (dom/div
          (css/add-class :hero-content)))
      (grid/row-column
        nil
        (dom/div
          (->> (css/add-class :section-title)
               (css/text-align :center))
          (dom/h1 nil "About us"))
        (dom/p
          nil
          (dom/strong nil "SULO Live - short for Support Local - ")
          (dom/span nil "is an online marketplace where your can follow the work of your favorite creatives and buy their products in the same place. We are three founders who forged the idea from a desire to improve the ways we consume retail and make it easier to shop local online."))

        (dom/p nil
               (dom/strong nil "When you're out shopping in real life")
               (dom/span nil " there's a social element to it. You have the opportunity to connect with the person selling and hear about their background, techniques and products. You can even find like minded friends who happen to be in the same place at the same time. We felt that this social experience had been missing from online shopping, and decided that we want to find a way to bring that online. ")

               ;(dom/strong nil "We decided to find a way ")
               ;(dom/span nil " that could bring the social experience from shopping at a physical location online.  While working on our idea we started wondering, why has the online shopping experience remained so two dimensional? The three of us were in different locations around the world at this point, and after taking a break from brainstorming via Skype one night, Petter and Diana called Miriam in Vancouver from a bar in Spain and shouted ")
               ;(dom/em nil "\"Live streaming!\"")
               ;(dom/span nil " And voilà, SULO Live was born!")
               )
        (dom/p nil
               (dom/strong nil "At this point in time")
               (dom/span nil ", Miriam was finished with her studies in Vancouver, while Petter and Diana were spending their time coding in Spain. After an exciting night with Hookah and tea at a bar in Seville, Petter and Diana called Miriam up and shouted ")
               (dom/em nil "\"Live streaming!\"")
               (dom/span nil " Et voilà, SULO Live was born!"))
        (dom/p
          nil
          (dom/strong nil "We think that everyone")
          (dom/span nil " should be able to create their own lifestyle and live off their passions if they want to. We love every day we get to interact with inspiring local entrepreneurs who want to use SULO Live to grow their business. By shopping local, you not only contribute to your local economy and a more sustainable way of life, you also help support a dream and a vision for the future."))
        ;(dom/p
        ;  nil
        ;  (dom/strong nil "By shopping local")
        ;  (dom/span nil ""))
        (dom/p
          nil
          (dom/strong nil "We belive that global change starts local")
          (dom/span nil ". Where are you local?"))
        (dom/p nil "SULO Love")
        (dom/div
          (->> (css/add-class :section-title)
               (css/text-align :center))
          (dom/h2 nil "Our team"))
        (grid/row
          (grid/columns-in-row {:small 1 :medium 3})
          (grid/column
            (css/text-align :center)
            (dom/h3 nil "Miriam")
            (photo/circle {:photo-id       "v1497760639/static/about-miriam"
                           :transformation :transformation/thumbnail-large})
            ;(dom/p nil (dom/span nil "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book."))
            (dom/p nil
                   (social/follow-button {:platform :social/instagram
                                          :href     "https://www.instagram.com/msmme29/"})
                   (social/follow-button {:platform :social/email
                                          :href     "mailto:miriam@sulo.live"})

                   ))
          (grid/column
            (css/text-align :center)
            (dom/h3 nil "Petter")
            (photo/circle {:photo-id       "static/about-petter"
                           :transformation :transformation/thumbnail-large})
            ;(dom/p nil (dom/span nil "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book."))
            (dom/p nil
                   (social/follow-button {:platform :social/twitter
                                          :href     "https://twitter.com/petterik_"})
                   (social/follow-button {:platform :social/email
                                          :href     "mailto:petter@sulo.live"})

                   ))
          (grid/column
            (css/text-align :center)
            (dom/h3 nil "Diana")
            (photo/circle {:photo-id       "static/about-diana"
                           :transformation :transformation/thumbnail-large})
            ;(dom/p nil (dom/span nil "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book."))
            (dom/p nil
                   (social/follow-button {:platform :social/instagram
                                          :href     "https://www.instagram.com/dianagr/"})
                   (social/follow-button {:platform :social/email
                                          :href     "mailto:diana@sulo.live"})

                   )))))))

(router/register-component :about AboutSulo)