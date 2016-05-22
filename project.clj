(defproject pjstadig/wayfarer "0.2.0"
  :description "A simple, programmable migration library."
  :url "https://github.com/pjstadig/wayfarer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :deploy-repositories [["releases" :clojars]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.6.0"]]
  :test-selectors {:default :postgresql
                   :all (constantly true)
                   :mysql :mysql
                   :postgresql :postgresql}
  :global-vars {*warn-on-reflection* true}
  :profiles
  {:dev {:dependencies [[mysql/mysql-connector-java "5.1.38"]
                        [org.postgresql/postgresql "9.4.1208.jre7"]]}})
