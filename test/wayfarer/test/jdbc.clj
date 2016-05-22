;;;; Copyright © Paul Stadig.  All rights reserved.
;;;;
;;;; This program and the accompanying materials are made available under the
;;;; terms of the Eclipse Public License v1.0 which accompanies this
;;;; distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
(ns wayfarer.test.jdbc
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.stacktrace :refer [print-cause-trace]]
   [clojure.string :refer [join]]
   [clojure.test :refer [is]]
   [wayfarer.core :refer [completed-migration-ids init migrate]]))

(def test-schema
  "wayfarer_test_schema")

(defn drop-table
  [conn table-name]
  (jdbc/execute! conn (str "DROP TABLE " table-name)))

(defn fixture
  [db-name url tables f]
  (try
    (jdbc/with-db-connection [conn url]
      (doseq [table (tables conn test-schema)]
        (drop-table conn (str test-schema "." table)))
      (doseq [table (tables conn)]
        (drop-table conn table)))
    (f)
    (catch Exception e
      (println (.getMessage e))
      (println "Failed to connect to" (str db-name ";") "skipping" db-name
               "tests"))))

(def migrations
  [{:id #uuid "c824fb41-e252-4522-9713-4554b4a8f693"
    :migration (join " "
                     ["CREATE TABLE IF NOT EXISTS"
                      "foo(id BIGINT PRIMARY KEY)"])}])

(def schema-migrations
  [{:id #uuid "eb296588-577a-4493-b47e-2a338c10dbd3"
    :migration (join " "
                     ["CREATE TABLE IF NOT EXISTS"
                      (str test-schema ".foo")
                      "(id BIGINT PRIMARY KEY)"])}])

(defn migrate-test
  [url tables]
  (letfn
      [(table-exists?
         ([url table-name]
          (some #{table-name} (tables url)))
         ([url schema table-name]
          (some #{table-name} (tables url schema))))]
    (let [store (init {:backend :jdbc :url url :schema test-schema})]
      (is (not (table-exists? url test-schema "foo")))
      (migrate store schema-migrations)
      (is (contains? (completed-migration-ids store)
                     "eb296588-577a-4493-b47e-2a338c10dbd3"))
      (is (table-exists? url test-schema "foo"))
      (migrate store schema-migrations))
    (let [store (init {:backend :jdbc :url url})]
      (is (not (table-exists? url "foo")))
      (migrate store migrations)
      (is (contains? (completed-migration-ids store)
                     "c824fb41-e252-4522-9713-4554b4a8f693"))
      (is (table-exists? url "foo"))
      (migrate store migrations))))
