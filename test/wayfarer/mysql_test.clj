;;;; Copyright © Paul Stadig.  All rights reserved.
;;;;
;;;; This program and the accompanying materials are made available under the
;;;; terms of the Eclipse Public License v1.0 which accompanies this
;;;; distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
(ns wayfarer.mysql-test
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.string :refer [join]]
   [clojure.test :refer [deftest is use-fixtures]]
   [wayfarer.test.jdbc :as test]))

(def database "wayfarer_test")
(def user (or (System/getenv "MYSQL_USER") "wayfarer"))
(def password (or (System/getenv "MYSQL_PWD") "wayfarer"))

(def url
  (format "jdbc:mysql://localhost/%s?user=%s&password=%s"
          database user password))

(defn tables
  [url]
  (let [sql ["SELECT table_name"
             "FROM information_schema.tables"
             "WHERE table_schema = ?"]]
    (jdbc/query url
                [(join " " sql) database]
                {:row-fn :table_name})))

(use-fixtures :once
  (partial test/fixture "MySQL" url tables))

(deftest ^:mysql t-migrate
  (test/migrate-test url tables))
