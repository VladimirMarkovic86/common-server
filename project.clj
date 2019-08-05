(defproject org.clojars.vladimirmarkovic86/common-server "0.3.48"
  :description "Common server"
  :url "http://github.com/VladimirMarkovic86/common-server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojars.vladimirmarkovic86/mongo-lib "0.2.13"]
                 [org.clojars.vladimirmarkovic86/session-lib "0.2.30"]
                 [org.clojars.vladimirmarkovic86/language-lib "0.2.38"]
                 [org.clojars.vladimirmarkovic86/dao-lib "0.3.30"]
                 [org.clojars.vladimirmarkovic86/ajax-lib "0.1.14"]
                 [org.clojars.vladimirmarkovic86/utils-lib "0.4.13"]
                 [org.clojars.vladimirmarkovic86/common-middle "0.2.12"]
                 [org.clojars.vladimirmarkovic86/pdflatex-lib "0.1.9"]
                 [org.clojars.vladimirmarkovic86/mail-lib "0.1.3"]
                 ]

  :min-lein-version "2.0.0"
  
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  
  :jar-exclusions [#"^reports/"])

