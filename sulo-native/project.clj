(defproject sulo-native "0.1.0-SNAPSHOT"
            :description "FIXME: write description"
            :url "http://example.com/FIXME"
            :license {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}
            :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                           [org.clojure/clojurescript "1.9.660"]
                           ;; Depending on transit-clj for faster cljs builds
                           [com.cognitect/transit-clj "0.8.300"]
                           [com.cognitect/transit-cljs "0.8.239"]
                           ;; Using our own com.taoensso/encore to exclude cljs.test and
                           ;; cljs.pprint from advanced mode compilation.
                           [org.clojars.petterik/encore "2.91.1-SNAPSHOT"]
                           [com.taoensso/timbre "4.10.0"]
                           [com.taoensso/sente "1.11.0"]
                           [org.clojure/core.async "0.3.442"]
                           [org.clojars.petterik/om "1.0.0-alpha49-SNAPSHOT-3" :exclusions [cljsjs/react cljsjs/react-dom]]

                           [com.andrewmcveigh/cljs-time "0.4.0"]
                           [cljs-http "0.1.39"]
                           [datascript "0.15.5"]
                           [datascript-transit "0.2.2"
                            :exclusions [com.cognitect/transit-clj
                                         com.cognitect/transit-cljs]]
                           [cljsjs/stripe "2.0-0"]
                           [cljsjs/quill "1.1.0-3"]
                           [cljsjs/react-select "1.0.0-rc.1"]
                           [cljsjs/react-grid-layout "0.13.7-0"]
                           [cljsjs/firebase "4.0.0-0"]
                           [bidi "2.0.10"]
                           [kibu/pushy "0.3.6"]
                           [inflections "0.13.0"]
                           [medley "0.8.3"]
                           [com.cemerick/url "0.1.1"]

                           ;; Dependencies only for compiling ../src
                           [com.datomic/datomic-pro "0.9.5544" :exclusions [joda-time]]
                           [com.google.guava/guava "21.0"]
                           [javax.mail/javax.mail-api "1.6.0"]
                           [clj-http "3.3.0"]
                           [buddy/buddy-auth "1.3.0"]
                           [environ "1.1.0"]
                           [org.martinklepsch/s3-beam "0.6.0-alpha1"]
                           ]
            :plugins [[lein-cljsbuild "1.1.5"]
                      [lein-shell "0.5.0"]
                      [lein-figwheel "0.5.10"]]
            :clean-targets ["target/" 
                            ;; don't clean these, because figwheel will remove them.
                            ;; "index.ios.js" "index.android.js" 
                            #_($PLATFORM_CLEAN$)]
            :aliases {"pod-deps"   ["shell" "pod" "install" "--project-directory=./ios"]
                      "prod-build" ^{:doc "Recompile code with prod profile."}
                                   ["do"
                                    "clean"
                                    ["shell" "bash" "-c" "cd .. && lein install"]
                                    ["with-profile" "prod" "cljsbuild" "once"]]}

            :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                             :username ~(System/getenv "DATOMIC_EMAIL")
                                             :password ~(System/getenv "DATOMIC_KEY")}}

            :figwheel {:validate-config :ignore-unknown-keys
                       :css-dirs        ["resources/public/assets/css/"]
                       :server-port     3450
                       ;;       :reload-clj-files {:clj true :cljc true}
                       }

            :profiles {:dev  {:dependencies [[figwheel-sidecar "0.5.10"]
                                             [com.cemerick/piggieback "0.2.1"]
                                             [binaryage/devtools "0.9.4"]]
                              :source-paths ["src" "env/dev" "../src"]
                              :cljsbuild    {:builds [
                                                      {:id           "ios"
                                                       :source-paths ["src" "env/dev" "../src"]
                                                       :figwheel     true
                                                       :compiler     {:output-to      "target/ios/not-used.js"
                                                                      :main           "env.ios.main"
                                                                      :output-dir     "target/ios"
                                                                      :parallel-build true
                                                                      :optimizations  :none}}
                                                      {:id           "android"
                                                       :source-paths ["src" "env/dev" "../src"]
                                                       :figwheel     true
                                                       :compiler     {:output-to      "target/android/not-used.js"
                                                                      :main           "env.android.main"
                                                                      :output-dir     "target/android"
                                                                      :parallel-build true
                                                                      :optimizations  :none}}
                                                      #_($DEV_PROFILES$)]}
                              :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
                       ;; Depends on the sulo project's jar.
                       :prod {:dependencies [[budget "0.1.0-SNAPSHOT"]
                                             [react-native-externs "0.0.3"]]
                              :cljsbuild    {:builds [{:id           "ios"
                                                       :source-paths ["src" "env/prod"]
                                                       :compiler     {:output-to          "index.ios.js"
                                                                      :main               "env.ios.main"
                                                                      :output-dir         "target/ios"
                                                                      :static-fns         true
                                                                      :optimize-constants true
                                                                      :optimizations      :advanced
                                                                      :parallel-build     true
                                                                      :verbose            true
                                                                      :language-in        :ecmascript5
                                                                      :closure-defines    {"goog.DEBUG" false}}}
                                                      {:id           "ios-local"
                                                       :source-paths ["src" "env/prod"]
                                                       :compiler     {:output-to          "index.ios.js"
                                                                      :main               "env.ios.local-main"
                                                                      :output-dir         "target/ios"
                                                                      :static-fns         true
                                                                      :optimize-constants true
                                                                      :optimizations      :advanced
                                                                      :parallel-build     true
                                                                      :verbose            true
                                                                      :language-in        :ecmascript5
                                                                      :closure-defines    {"goog.DEBUG" false}}}
                                                      #_({:id           "android"
                                                          :source-paths ["src" "env/prod"]
                                                          :compiler     {:output-to          "index.android.js"
                                                                         :main               "env.android.main"
                                                                         :output-dir         "target/android"
                                                                         :static-fns         true
                                                                         :optimize-constants true
                                                                         :optimizations      :advanced
                                                                         :parallel-build     true
                                                                         :language-in        :ecmascript5
                                                                         :closure-defines    {"goog.DEBUG" false}}})
                                                      #_($PROD_PROFILES$)]}}})
                                                  
                      
