(ns eponai.web.ui.tos
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [eponai.web.ui.footer :as foot]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.css :as css]))

(defui ToS
  static om/IQuery
  (query [this]
    [:query/current-route
     {:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/footer (om/get-query foot/Footer)}])
  Object
  (render [this]
    (let [{:proxy/keys [navbar footer]} (om/props this)]
      (common/page-container
        {:id     "sulo-tos"
         :navbar navbar
         :footer footer}

        (grid/row-column
          nil
          (dom/h1 (css/text-align :center) "Terms of Service")
          (dom/p nil (dom/span nil "Eponai hb (“eponai”, ”SULO Live”, the “Company”, “we” or “us”) owns and operates the sulo.live website and all related subdomains (the “Website”). These terms of service (the “Terms”) govern your use of the Website and other services we offer (collectively, the “Services”). By using the Services in any way, you accept these Terms. If you do not wish to be bound by these Terms, do not use the Services. Please read these carefully before you start to use the Services."))

          (dom/section
            nil
            (dom/h2 nil "Acceptance of terms")
            (dom/p nil (dom/span nil "These Terms comprise an electronic contract that establishes the legally binding terms you must accept to use the Services. The Terms include SULO Live’s Privacy Policy."))
            (dom/p nil (dom/span nil "By accessing or using the Services, you accept the Terms and agree to the terms, conditions and notices contained or referenced herein and consent to have the Terms and all notices provided to you in electronic form. The Terms may be modified by SULO Live from time to time, such modifications to be effective upon posting by us on the Website. We shall notify you of changes to the Terms through notices on the Website or by email, or both. To withdraw your consent, you must cease using the Services and terminate your Account. "))

            (dom/p nil (dom/span nil "If you breach any provision of these Terms, your right to access and use the Services shall cease immediately.")))

          (dom/section
            nil
            (dom/h2 nil "The SULO Live service")
            (dom/p nil (dom/span nil "SULO Live is an e-commerce website with integrated live streaming. It is a technology platform that connects businesses, makers, and artisans (the “Sellers”) selling their products (the “Products”) with customers (the “Customers”). The Sellers and Customers are both users of the Services and are herein after referred to collectively as \"Users.\""))
            (dom/p nil (dom/span nil "SULO Live provides an independent service that allows Customers to connect with, browse, and purchase Products directly from Sellers. Any decision by a User to enter into a transaction with another User is a decision made in such User’s sole discretion and at their own risk."))

            (dom/p nil (dom/span nil "Return and Refund policies, shipping policies, and shipping rates are determined by each individual Seller, and therefore may vary of Seller to Seller. It is the User’s responsibility to be aware of such policies. These policies will be stated as part of each Seller’s Profile. Questions in regards to these policies shall be directed by the Customer directly to the Seller."))

            (dom/p nil (dom/span nil "SULO Live does not have control over the actions of any User or the quality, suitability, reliability, durability, legality, or any other aspect of any Product. As such, SULO Live makes no representations or warranties whatsoever with respect to the Products or the actions of any User or third party. You understand that SULO Live does not routinely screen its Users, inquire into the background of its Users, or attempt to verify information provided by any User. SULO Live does not verify or confirm that any User is who they claim to be or is accurately representing themselves or their Products. Information provided as part of the Services is for general purposes only. SULO Live does not assume any responsibility for the accuracy or reliability of this information or any information provided through the Services."))
            (dom/p nil (dom/span nil "We reserve the right to add, amend, delete, edit, remove or modify any information, content, material or data displayed on the Website and without notice from time to time. We reserve the right to suspend or discontinue the Services, in whole or in part, without notice. We shall not be liable to you for any modification, suspension, or discontinuation of the Services."))
            (dom/p nil (dom/span nil "SULO Live shall have no obligation to provide you with any support or maintenance in connection with the Services.")))

          (dom/section
            nil
            (dom/h2 nil "Permitted use of website")
            (dom/p nil "We grant you a non-transferable, non-exclusive, revocable, limited licence to use and access the Website")
            (dom/p nil (dom/span nil "You are responsible for paying all fees that you owe to SULO Live. You are also solely responsible for collecting and/or paying any applicable taxes for any purchases or sales you make through our Services."))
            (dom/p nil (dom/span nil "You are not permitted to use the Website:"))
            (dom/ol {:type "a"}
                    (dom/li nil (dom/p nil (dom/span nil "in any unlawful, fraudulent, or commercial manner, or any other manner prohibited by the Terms;")))
                    (dom/li nil (dom/p nil (dom/span nil "to upload, transmit, or distribute to or through the Website any computer viruses, worms, or any software intended to damage or alter a computer system or data;")))
                    (dom/li nil (dom/p nil (dom/span nil "to send through the Website unsolicited or unauthorized advertising, promotional materials, junk mail, spam, chain letters, pyramid schemes, or any other form of duplicative or unsolicited messages, whether commercial or otherwise;")))
                    (dom/li nil (dom/p nil (dom/span nil "to use the Website to harvest, collect, gather or assemble information or data regarding other Users, including e-mail addresses, without their consent;")))
                    (dom/li nil (dom/p nil (dom/span nil "to interfere with, disrupt, or create an undue burden on servers or networks connected to the Website, or violate the regulations, policies or procedures of such networks;")))
                    (dom/li nil (dom/p nil (dom/span nil "to attempt to gain unauthorized access to the Website (or to other computer systems or networks connected to or used together with the Website), whether through password mining or any other means;")))
                    (dom/li nil (dom/p nil (dom/span nil "to harass or interfere with any other Website User’s use and enjoyment of the Website;")))
                    (dom/li nil (dom/p nil (dom/span nil "to use software or automated agents or scripts to produce multiple accounts on the Website, or to generate automated searches, requests, or queries to (or to strip, scrape, or mine data from) the Website;")))
                    (dom/li nil (dom/p nil (dom/span nil "to tamper with, modify, copy without express permission, amend, make derivative or reverse engineer any part of the Website; or")))
                    (dom/li nil (dom/p nil (dom/span nil "to licence, sell, rent or lease any part of the Website.")))))

          (dom/section
            nil
            (dom/h2 nil "User account")

            (dom/p nil (dom/span nil "As part of your use of the Services, you may create a user account (an “Account”). Each User may have only one Account. You must be at least 19 years of age, or the age of legal majority in your jurisdiction (if different than 19), to obtain an Account. It is your responsibility to ensure that all information, content, material, or data you provide in your Account is at all times correct, complete, accurate, and not misleading. We accept no responsibility for any loss or damage to you arising from Account information that is not correct, complete, and accurate, or is misleading."))

            (dom/p nil (dom/span nil "It is your responsibility to keep all of your Account information confidential, including but not limited to your username, password and other identifying information. You are solely responsible for all activities undertaken through the Services using your username, password, or other Account information. You must notify us immediately of any unauthorized or suspected unauthorized use of your Account. We accept no responsibility for loss or damage resulting from failure to keep your information confidential or from failing to notify us of unauthorized use of your Account."))

            (dom/p nil (dom/span nil "As part of your Account, you may create a user profile (the “Profile”). Your Profile will only be displayed on the Website once you have inputted all of the mandatory information. Once complete, your Profile will be fully visible to other Users."))
            (dom/p nil (dom/span nil "You may delete your Account at any time by following the guidelines on the Website."))
            (dom/p nil (dom/span nil "We may, in our sole discretion, terminate your Account and your access to the Services at any time if you are in breach of this Agreement or the Terms of Use.")))

          (dom/section
            nil
            (dom/h2 nil "User content")
            (dom/p nil (dom/span nil "You shall retain ownership of any views, opinions, reviews, ratings, comments, content or material you submit, display, distribute, upload, post, share, publish or otherwise make publicly available on or through the Website or otherwise through the Services (the “User Content”). You grant (and you represent and warrant that you have the right to grant) to SULO Live an irrevocable, nonexclusive, royalty-free and fully paid, worldwide license to reproduce, distribute, publicly display and perform, prepare derivative works of, incorporate into other works including for marketing purposes, and otherwise use and exploit your User Content, and to grant sublicenses of the foregoing rights. You irrevocably waive (and agree to cause to be waived) any claims and assertions of moral rights or attribution with respect to the User Content."))
            (dom/p nil (dom/span nil "You are not to collect, upload, transmit, display, or distribute any User Content that:"))
            (dom/ol
              {:type "a"}
              (dom/li nil (dom/p nil (dom/span nil "violates any third-party right, including any copyright, trademark, patent, trade secret, moral right, privacy right, right of publicity, or any other intellectual property or proprietary right;")))
              (dom/li nil (dom/p nil (dom/span nil "is unlawful, harassing, abusive, tortious, threatening, harmful, invasive of another’s privacy, vulgar, defamatory, false, intentionally misleading, trade libelous, pornographic, obscene, patently offensive, promotes racism, bigotry, hatred, or physical harm of any kind against any group or individual or is otherwise objectionable;")))
              (dom/li nil (dom/p nil (dom/span nil "is harmful to minors in any way; or")))
              (dom/li nil (dom/p nil (dom/span nil "is in violation of any law, regulation, or obligations or restrictions imposed by any third party."))))

            (dom/p nil (dom/span nil "You are responsible for your User Content. We do not sponsor or endorse your User Content. We reserve the right (but have no obligation) to review any User Content, and to investigate and/or take appropriate action against you in our sole discretion if you violate the Terms or otherwise create liability for us or any third party. Such action may include removing or modifying your User Content, terminating your Account, and/or reporting you to law enforcement authorities."))
            (dom/p nil (dom/span nil "We make no guarantees regarding the accuracy, currency, suitability, or quality of any content from other Users. Your interactions with other Users are solely between you and such other Users. You agree that SULO Live will not be responsible for any loss or damage incurred as the result of any such interactions. If there is a dispute between you and any other user, we are under no obligation to become involved."))
            (dom/p nil (dom/span nil "We are not obligated to keep a backup of the User Content. We accept no liability for lost User Content.")))

          (dom/section
            nil
            (dom/h2 nil "Sellers")

            (dom/p nil
                   (dom/span nil "Payment processing services for Sellers on SULO Live are provided by Stripe and are subject to the ")
                   (dom/a {:href "https://stripe.com/ca/connect-account/legal"
                           :target "_blank"} (dom/span nil "Stripe Connected Account Agreement"))
                   (dom/span nil ", which includes the Stripe Terms of Service (collectively, the “Stripe Services Agreement”). By agreeing to these terms or continuing to operate as a Seller on SULO Live, you agree to be bound by the Stripe Services Agreement, as the same may be modified by Stripe from time to time. As a condition of SULO Live enabling payment processing services through Stripe, you agree to provide SULO Live accurate and complete information about you and your business, and you authorize SULO Live to share it and transaction information related to your use of the payment processing services provided by Stripe."))

            (dom/p nil (dom/span nil "When you as a Seller make a sale through SULO Live, you agree to the following fees being deducted from the transaction amount as the funds become available for deposit:"))
            (dom/ol {:type "a"}
                    (dom/li nil
                            (dom/p nil
                                   (dom/span nil "a ")
                                   (dom/em nil "commission fee")
                                   (dom/span nil " of 20% of the price you display for each product. The commission fee will not apply to the shipping cost, sales tax, Goods and Services Tax, or Harmonized Sales Tax, unless you have included those charges in your product price.")))
                    (dom/li nil
                            (dom/p nil
                                   (dom/span nil " a ")
                                   (dom/em nil "transaction fee")
                                   (dom/span nil " of 3% of the total amount of the sale, including tax and shipping.")))))

          (dom/section
            nil
            (dom/h2 nil "Payment services")

            (dom/p nil
                   (dom/span nil "SULO Live uses the third party payment platform Stripe and the Stripe API to process credit and debit card transactions through the Services. By using the Services and agreeing to the Terms, you also agree to be bound by Stripe’s Terms of Service (")
                   (dom/a {:href "https://stripe.com/ca/legal#section_c"} (dom/span nil "https://stripe.com/ca/legal#section_c"))
                   (dom/span nil ")."))
            (dom/p nil (dom/span nil "You expressly understand and agree that SULO Live shall not be liable for any payments and monetary transactions that occur through your use of the Services. You expressly understand and agree that all payments and monetary transactions are handled by Stripe. You agree that SULO Live shall not be liable for any issues regarding financial and monetary transactions between you, other Users, and any other party, including Stripe."))
            (dom/p nil (dom/span nil "You are responsible for all transactions (one-time, recurring, and refunds) processed through the Services and/or Stripe. SULO Live is not liable for loss or damage from errant or invalid transactions processed with your Stripe account. This includes transactions that were not processed due to a network communication error, or any other reason. If you process a transaction, it is your responsibility to verify that the transaction was successfully processed."))
            (dom/p nil (dom/span nil "You understand that SULO Live uses the Stripe API to run transactions through the Services and that the Stripe API is subject to change at any time and such changes may adversely affect the services provided as part of the Services. You understand and agree to not hold SULO Live liable for any adverse effects that actions (whether intentional or unintentional) on the part of Stripe may cause to your Stripe account, your SULO Live Account, or your business.")))

          (dom/section
            nil
            (dom/h2 nil "Third party links")
            (dom/p nil (dom/span nil "We may provide links through the Services to the websites of third parties. These websites are owned and operated by third parties over whom we do not have control. SULO Live has not reviewed all of the sites linked through the Services and accepts no responsibility for the contents of third party websites. The inclusion of any link does not imply endorsement by SULO Live of the site. Use of any such linked website is at the user's own risk. Any links to third party websites are provided for your interest and convenience only. We are not responsible or liable for any loss or damage you may suffer or incur in connection with your use of any third party websites or for any acts, omissions, errors or defaults of any third party in connection with their website.") ))

          (dom/section
            nil
            (dom/h2 nil "Intellectual property")
            (dom/p nil (dom/span nil "eponai owns and retains all proprietary rights in the Services, and in all content, trademarks, trade names, service marks and other intellectual property rights related thereto. The Services contain the copyrighted material, trademarks, and other proprietary information of SULO Live. You agree to not copy, modify, transmit, create any derivative works from, make use of, or reproduce in any way any copyrighted material, trademarks, trade names, service marks, or other intellectual property or proprietary information accessible through the Services. You agree to not remove, obscure or otherwise alter any proprietary notices appearing on any content, including copyright, trademark and other intellectual property notices."))
            (dom/p nil (dom/span nil "Subject to the limited licenses granted in these Terms, no licence is granted to you or any other party for the use of SULO Live’s intellectual property."))
            (dom/p nil (dom/span nil "Any third party trademarks, service marks or other intellectual property displayed on through the Services are used with the authorization of the owner of the intellectual property, subject to their guidelines for use. We cannot authorize you to use, reproduce or modify any third party intellectual property used in the Services, and are not responsible for any loss or damage you may suffer or incur in connection with your use of any third party intellectual property for your own purpose.")))

          (dom/section
            nil
            (dom/h2 nil "Disclaimers")
            (dom/p nil (dom/span nil "You agree that:"))
            (dom/ol {:type "a"}
                    (dom/li nil (dom/p nil (dom/span nil "If you use the Services, you do so at your own and sole risk. The Services are provided on an \"as is\" and \"as available\" basis. We expressly disclaim all warranties of any kind, whether express or implied, including, without limitation, implied warranties of merchantability, and fitness for a particular purpose, title and non-infringement.")))
                    (dom/li nil (dom/p nil (dom/span nil "If you access or transmit any content through the use of the Services, you do so at your own discretion and your sole risk. You are solely responsible for any loss or damage to you in connection with such actions. We are not responsible for any incorrect or inaccurate content in connection with the Services, whether caused by Users or by any of the programming associated with or utilized in the Services. We are not responsible for the conduct, whether online or offline, of any User of the Website. We assume no responsibility for any error, omission, interruption, deletion, defect, delay in operation or transmission, communications line failure, theft or destruction or unauthorized access to, or alteration of, user communications."))))

            (dom/p nil (dom/span nil "We do not warrant that:"))
            (dom/ol {:type "a"}
                    (dom/li nil (dom/p nil (dom/span nil "the Services will meet your requirements;")))
                    (dom/li nil (dom/p nil (dom/span nil "access to the Services will be uninterrupted, timely, secure, or error-free;")))
                    (dom/li nil (dom/p nil (dom/span nil "the quality or reliability of the Services will meet your expectations;")))
                    (dom/li nil (dom/p nil (dom/span nil "any information you provide or we collect will not be disclosed to third parties;")))
                    (dom/li nil (dom/p nil (dom/span nil "any account on the Website is accurate, up to date or authentic; or")))
                    (dom/li nil (dom/p nil (dom/span nil "third parties will not use your confidential information in an unauthorized manner."))))
            (dom/p nil (dom/span nil "Under no circumstances will we be responsible for any loss or damage, including personal injury or death, resulting from anyone's use of the Services or any conduct or interactions between Users of our site, whether online or offline.")))

          (dom/section
            nil
            (dom/h2 nil "Limitation of liability and release")
            (dom/p nil (dom/span nil "You agree that neither we nor our affiliates, officers, directors, employees, agents and licensors will be liable for any damages whatsoever, including direct, indirect, incidental, punative, special, consequential or exemplary damages, in connection with, or otherwise resulting from, any use of the Services, even if we have been advised of the possibility of such damages. We shall not be liable for any damages, liability, or losses arising from, relating to, or connected with:"))

            (dom/ol {:type "a"}
                    (dom/li nil (dom/p nil (dom/span nil "the use or inability to use the Services;")))
                    (dom/li nil (dom/p nil (dom/span nil "disclosure of, unauthorized access to or alteration of your Account;")))
                    (dom/li nil (dom/p nil (dom/span nil "actions or inactions of other Users or any other third parties for any reason; or")))
                    (dom/li nil (dom/p nil (dom/span nil "any other matter arising from, relating to or connected with the Services or these Terms.")))
                    )
            (dom/p nil (dom/span nil "We will not be liable for any failure or delay in performing under these Terms where such failure or delay is due to causes beyond our reasonable control, including natural catastrophes, governmental acts or omissions, laws or regulations, terrorism, labor strikes or difficulties, communication system breakdowns, hardware or software failures, transportation stoppages or slowdowns or the inability to procure supplies or materials."))
            (dom/p nil (dom/span nil "SULO Live expressly disclaims any liability that may arise between Users related to or arising from use of the Services. You hereby release and forever discharge SULO Live and its affiliates, officers, directors, employees, agents and licensors from any and all claims, demands, damages (actual or consequential) of every kind and nature, whether known or unknown, contingent or liquidated, arising from or related to any dispute or interactions with any other User, whether online or in person, whether related to the use of the Services or otherwise."))
            (dom/p nil (dom/span nil "You acknowledge and agree that the disclaimers of warranties above and these limitations of liability are an agreed upon allocation of risk between you and us. You acknowledge and agree that if you did not agree to these limitations of liability you would not be permitted to access the Services. You acknowledge and agree that such provisions are reasonable and fair.")))

          (dom/section
            nil
            (dom/h2 nil "Indemnity")
            (dom/p nil (dom/span nil "You agree to defend, indemnify and hold us, and our subsidiaries, parents, affiliates, and each of our and their directors, officers, managers, partners, agents, other representatives, employees and customers (each an “Indemnified Party” and collectively, the “Indemnified Parties”), harmless from any claim, demand, action, damage, loss, cost or expense, including without limitation, lawyers' fees and costs, investigation costs and settlement expenses, incurred in connection with any investigation, claim, action, suit or proceeding of any kind brought against any Indemnified Party arising out of your use of the Services, any alleged or actual infringement of the intellectual property rights of any party, any injury or damage to property or person, any act by you in connection with any user of the Services or any other third party, or alleging facts or circumstances that could constitute a breach by you of any provision of these Terms and/or any of the representations and warranties set forth above.")))

          (dom/section
            nil
            (dom/h2 nil "Privacy")
            (dom/p nil (dom/span nil "We are committed to protecting your privacy. We process your information in line with our Privacy Policy. By using the Services, you agree to the way in which we process and deal with your personal information.")))
          (dom/section
            nil
            (dom/h2 nil "Term and termination")
            (dom/p nil (dom/span nil "These Terms will remain in full force and effect while you use the Services and/or have an Account."))
            (dom/p nil (dom/span nil "You acknowledge and agree that we, in our sole discretion, may terminate your access to the Services for any reason, including, without limitation, your breach of these Terms. You understand and agree that we are not required, and may be prohibited, from disclosing to you the reason for termination of your access to the Services. You acknowledge and agree that any termination of your access to the Services may be effected without prior notice, and acknowledge and agree that we may immediately deactivate or delete your account and bar any further access to the Services. Further, you acknowledge and agree that we will not be liable to you or any third party for any costs or damages of any kind for or resulting from any termination of your access to our Services. Upon termination, your information may be deleted or kept as necessary.")))


          (dom/section
            nil
            (dom/h2 nil "Dispute resolution")
            (dom/p nil (dom/span nil "In the event a dispute arises out of or in connection with these Terms, the parties shall attempt to resolve the dispute through friendly consultation."))
            (dom/p nil (dom/span nil "If the dispute is not resolved within a reasonable period then any or all outstanding issues shall be referred to mediation on notice by one party to the other, with the assistance of a neutral mediator jointly selected by the parties. If the dispute cannot be settled within thirty (30) days after the mediator has been appointed, or within such other period as agreed to by the parties in writing, either party may refer the dispute to arbitration under the International Commercial Arbitration Rules of Procedure of the British Columbia International Commercial Arbitration Centre (the “BCICAC”). The appointing authority shall be the BCICAC and the case shall be administered by the BCICAC in accordance with its Rules.")))

          (dom/section
            nil
            (dom/h2 nil "Feedback")
            (dom/p nil (dom/span nil "If you provide us with any feedback or suggestions regarding the Services (“Feedback”), you hereby assign to SULO Live all rights in such Feedback and agree that SULO Live shall have the right to use and fully exploit such Feedback and related information in any manner it deems appropriate. We will treat any Feedback you provide to us as non-confidential and non-proprietary. You agree that you will not submit to us as Feedback any information or ideas that you consider to be confidential or proprietary.")))

          (dom/section
            nil
            (dom/h2 nil "General")
            (dom/p nil (dom/span nil "This Agreement shall be governed by, and construed under, the laws of the Province of British Columbia."))
            (dom/p nil (dom/span nil "In the event that any portion of this Agreement is held to be unenforceable, the unenforceable portion shall be construed in accordance with applicable law as nearly as possible to reflect its original intentions and the remainder of the provisions shall remain in full force and effect."))
            (dom/p nil (dom/span nil "No failure or delay by either party in exercising any right under this Agreement shall constitute a waiver of that right."))
            (dom/p nil
                   (dom/span nil "If you have any questions or concerns about these Terms, you may contact us at ")
                   (dom/a {:href "mailto:hello@sulo.live"}
                          (dom/span nil "hello@sulo.live"))
                   (dom/span nil "."))))))))

(router/register-component :tos ToS)