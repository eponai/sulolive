(ns eponai.server.ui.terms
  (:require
    [eponai.server.ui.common :as common :refer [text-javascript]]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Terms
  Object
  (render [this]
    (let [{:keys [release?]} (om/props this)]
      (dom/html
        nil
        (apply dom/head nil (common/head release?))
        (prn "Testing")
        (dom/body
          nil
          ;(common/inline-javascript (common/iubenda-code))
          ;(common/inline-javascript (common/facebook-async-init-code))
          ;(common/anti-forgery-field)
          (dom/div {:id "jm-terms"}

            (dom/div {:id "header"}
              (dom/nav {:className "top-bar"}
                       (dom/div {:className "top-bar-left"}
                         (dom/a {:className "navbar-brand" :href "/"}
                                (dom/strong nil "jourmoney")))))

            (dom/div {:id "page-content" :className "row column"}
              (dom/h1 nil "Terms of Service")
              (dom/p
                nil
                "These terms of service (\"Terms\", \"Agreement\") are an agreement between eponai hb (\"eponai hb\", \"us\", \"we\" or \"our\") and you (\"User\", \"you\" or \"your\"). This Agreement sets forth the general terms and conditions of your use of the" (dom/strong nil " www.jourmoney.com ") "website and any of its products or services (collectively, \"Website\" or \"Services\").")
              (dom/h2 nil "Accounts and membership")
              (dom/p
                nil
                "You must be at least 13 years of age to use www.jourmoney.com. By using www.jourmoney.com and by agreeing to this Agreement you warrant and represent that you are at least 13 years of age. If you create an account at www.jourmoney.com, you are responsible for maintaining the security of your account and you are fully responsible for all activities that occur under the account and any other actions taken in connection with it. Providing false contact information of any kind may result in the termination of your account. You must immediately notify us of any unauthorized uses of your account or any other breaches of security. We will not be liable for any acts or omissions by you, including any damages of any kind incurred as a result of such acts or omissions. We may suspend, disable, or delete your account (or any part thereof) if we determine that you have violated any provision of this Agreement or that your conduct or content would tend to damage our reputation and goodwill. If we delete your account for the foregoing reasons, you may not re-register on www.jourmoney.com. We may block your email address and Internet protocol address to prevent further registration.")

              (dom/p nil "To register and activate your account on www.jourmoney.com you must either ")
              (dom/ul nil
                      (dom/li nil "provide an email address, and the confirmation of its validity by clicking on a link sent by email; or")
                      (dom/li nil "log in using your Facebook account in accordance with Facebook's Terms of Service"))

              (dom/h2 nil "Billing and payments")
              (dom/p
                nil
                "www.jourmoney.com requires a monthly subscription which will automatically renew each month. If it is expired or otherwise canceled, access to www.jourmoney.com may be restricted. ")

              (dom/p nil "You can register to a free trial, and use the app for free for 7 days. After the trial period is ended, you are required to provide a payment option for your subscription. You decide how much you want to pay each month (including nothing), you can update that anytime and will be charged automatically the amount you selected. We reserve the right to change products and product pricing at any time and will notify you if that happens.")

              (dom/p nil "You can cancel your subscription anytime by removing your payment options.")

              (dom/h2 nil "Accuracy of information")
              (dom/p nil "Occasionally there may be information on www.jourmoney.com that contains typographical errors, inaccuracies or omissions that may relate to product descriptions, pricing, promotions and offers. We reserve the right to correct any errors, inaccuracies or omissions, and to change or update information or cancel orders if any information on www.jourmoney.com is inaccurate at any time without prior notice. We undertake no obligation to update, amend or clarify information on www.jourmoney.com including, without limitation, pricing information, except as required by law. No specified update or refresh date applied on www.jourmoney.com should be taken to indicate that all information on www.jourmoney.com has been modified or updated.")

              (dom/h2 nil "Backups")
              (dom/p nil "We are not responsible for Content residing on www.jourmoney.com. In no event shall we be held liable for any loss of any Content. It is your sole responsibility to maintain appropriate backup of your Content. Notwithstanding the foregoing, on some occasions and in certain circumstances, with absolutely no obligation, we may be able to restore some or all of your data that has been deleted as of a certain date and time when we may have backed up data for our own purposes. We make no guarantee that the data you need will be available.")

              (dom/h2 nil "Prohibited uses")
              (dom/p nil "In addition to other terms as set forth in the Agreement, you are prohibited from using the website or its content:")
              (dom/ul
                nil
                (dom/li nil "for any unlawful purpose;")
                (dom/li nil "to solicit others to perform or participate in any unlawful acts; ")
                (dom/li nil "to violate any international, federal, provincial or state regulations, rules, laws, or local ordinances;")
                (dom/li nil "to infringe upon or violate our intellectual property rights or the intellectual property rights of others;")
                (dom/li nil "to harass, abuse, insult, harm, defame, slander, disparage, intimidate, or discriminate based on gender, sexual orientation, religion, ethnicity, race, age, national origin, or disability;")
                (dom/li nil "to submit false or misleading information;")
                (dom/li nil "to upload or transmit viruses or any other type of malicious code that will or may be used in any way that will affect the functionality or operation of the Service or of any related website, other websites, or the Internet;")
                (dom/li nil "to collect or track the personal information of others;")
                (dom/li nil "to spam, phish, pharm, pretext, spider, crawl, or scrape;")
                (dom/li nil "for any obscene or immoral purpose; or")
                (dom/li nil "to interfere with or circumvent the security features of www.jourmoney.com or any related website, other websites, or the Internet. We reserve the right to terminate your use of the Service or any related website for violating any of the prohibited uses."))


              (dom/h2 nil "Disclaimer of warranty")
              (dom/p nil "You agree that your use of our Website or Services is solely at your own risk. You agree that such Service is provided on an \"as is\" and \"as available\" basis. We expressly disclaim all warranties of any kind, whether express or implied, including but not limited to the implied warranties of merchantability, fitness for a particular purpose and non-infringement. We make no warranty that the Services will meet your requirements, or that www.jourmoney.com will be uninterrupted, timely, secure, or error free; nor do we make any warranty as to the results that may be obtained from the use of www.jourmoney.com or as to the accuracy or reliability of any information obtained through www.jourmoney.com or that defects in www.jourmoney.com will be corrected. You understand and agree that any material and/or data downloaded or otherwise obtained through the use of www.jourmoney.com is done at your own discretion and risk and that you will be solely responsible for any damage to your computer system or loss of data that results from the download of such material and/or data. We make no warranty regarding any services purchased or obtained through www.jourmoney.com or any transactions entered into www.jourmoney.com. No advice or information, whether oral or written, obtained by you from us or through www.jourmoney.com shall create any warranty not expressly made herein.")

              (dom/h2 nil "Changes and amendments")
              (dom/p nil "We reserve the right to modify this Agreement or its policies relating to the www.jourmoney.com at any time, effective upon posting of an updated version of this Agreement on www.jourmoney.com. When we do we will  revise the updated date at the bottom of this page. Continued use of www.jourmoney.com after any such changes shall constitute your consent to such changes.")

              (dom/h2 nil "Acceptance of these terms")
              (dom/p nil "You acknowledge that you have read this Agreement and agree to all its terms and conditions. By using www.jourmoney.com you agree to be bound by this Agreement. If you do not agree to abide by the terms of this Agreement, you are not authorized to use or access www.jourmoney.com and its Services.")

              (dom/h3 nil "Contacting us")
              (dom/p nil "If you have any questions about this Policy, please contact us at " (dom/a {:className "mail-link"
                                                                                                      :href      "mailto:info@jourmoney.com"} "info@jourmoney.com") "."))
            (dom/footer
              {:className "footer"}
              (dom/ul {:className "menu"}
                      (dom/li nil
                              (dom/small nil
                                         "Say hi to us anytime at "
                                         (dom/a {:className "mail-link"
                                                 :href      "mailto:info@jourmoney.com"}
                                                "info@jourmoney.com"))))
              (dom/ul {:className "menu"}
                      (dom/li nil
                              (dom/small nil "Copyright © eponai 2016. All Rights Reserved"))))))))))

