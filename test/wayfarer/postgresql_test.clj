;;;; Copyright © Paul Stadig.  All rights reserved.
;;;;
;;;; This program and the accompanying materials are made available under the
;;;; terms of the Eclipse Public License v1.0 which accompanies this
;;;; distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
(ns wayfarer.postgresql-test
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.string :refer [join]]
   [clojure.test :refer [deftest is use-fixtures]]
   [wayfarer.test.jdbc :as test]))

(def database "wayfarer_test")
(def user (or (System/getenv "PGUSER") "wayfarer"))
(def password (or (System/getenv "PGPASSWORD") "wayfarer"))

(def url
  (format "jdbc:postgresql://localhost/%s?user=%s&password=%s"
          database user password))

(defn tables
  ([url]
   (tables url "public"))
  ([url schema]
   (let [sql ["SELECT tablename"
              "FROM pg_catalog.pg_tables"
              "WHERE schemaname = ?"
              " AND tableowner = ?"]]
     (jdbc/query url
                 [(join " " sql)
                  schema
                  user]
                 {:row-fn :tablename}))))

(use-fixtures :once
  (partial test/fixture "PostgreSQL" url tables))

(deftest ^:postgresql t-migrate
  (test/migrate-test url tables))
