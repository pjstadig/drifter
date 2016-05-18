;;;; Copyright © Paul Stadig.  All rights reserved.
;;;;
;;;; This program and the accompanying materials are made available under the
;;;; terms of the Eclipse Public License v1.0 which accompanies this
;;;; distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
(ns wayfarer.jdbc
  "A JDBC backend implementation for wayfarer.  This backend can be used to run
  migrations against a JDBC database.  It supports the following initialization
  options:

  :url -> the JDBC URL of the database that should be migrated

  An example URL is:

  \"jdbc:postgresql://localhost/wayfarer?user=wayfarer&password=wayfarer\"

  Upon initialization, wayfarer will create a table named \"migrations\" (if it
  does not already exist), and use that for tracking which migrations have been
  completed.

  A migration is a hash map with the following keys:

  :id -> a UUID that uniquely identifies this migration
  :migration -> a string; the SQL command to be executed

  An example migration is:

  {:id #uuid \"ebbb9b5a-fc77-4b64-9e13-8caf4c17cd8f\"
    :migration (string/join \" \" \"CREATE TABLE users(\"
                                \"  id BIGSERIAL PRIMARY KEY,\"
                                \"  name VARCHAR(2000),\"
                                \"  created TIMESTAMP(6) NOT NULL DEFAULT 'now'\"
                                \")\")}

  A single transaction is used both for running the SQL command and for marking
  the migration complete.  This means in databases (like PostgreSQL) that
  support transactional DDL both the migration and marking it complete will
  succeed or neither will."
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.string :refer [join]]
   [wayfarer.protocol :as proto]))

(defrecord Transaction
    [conn auto-commit transaction-isolation read-only?]
  java.lang.AutoCloseable
  java.io.Closeable
  (close [this]
    (doto (jdbc/db-connection conn)
      (.setAutoCommit auto-commit)
      (.setTransactionIsolation transaction-isolation)
      (.setReadOnly read-only?)))
  proto/ITransaction
  (execute [this {:keys [id migration]}]
    (jdbc/execute! conn migration)
    (jdbc/insert! conn
                  "migrations"
                  {:id (str id)
                   :created (-> (java.util.Date.)
                                .getTime
                                (java.sql.Date.))}))
  (rollback [this]
    (.rollback (jdbc/db-connection conn)))
  (commit [this]
    (.commit (jdbc/db-connection conn))))

(defrecord Connection
    [conn]
  proto/IConnection
  (completed-migration-ids [this]
    (set
     (jdbc/with-db-transaction [conn conn]
       (jdbc/query conn
                   ["SELECT id FROM migrations ORDER BY created"]
                   {:row-fn (comp str :id)}))))
  (start-transaction [this]
    (let [sql-conn (jdbc/db-connection conn)
          auto-commit (.getAutoCommit sql-conn)
          transaction-isolation (.getTransactionIsolation sql-conn)
          read-only? (.isReadOnly sql-conn)]
      (doto sql-conn
        (.setAutoCommit false)
        (.setTransactionIsolation
         java.sql.Connection/TRANSACTION_REPEATABLE_READ)
        (.setReadOnly false))
      (->Transaction conn auto-commit transaction-isolation read-only?)))
  java.lang.AutoCloseable
  java.io.Closeable
  (close [this]
    (.close (jdbc/db-connection conn))))

(defrecord Store
    [url]
  proto/IStore
  (connect [this]
    (let [conn (jdbc/get-connection url)]
      (try
        (->Connection (jdbc/add-connection url conn))
        (catch Throwable t
          (.close conn))))))

(def migrations
  [["CREATE TABLE IF NOT EXISTS migrations("
    "  id VARCHAR(36) PRIMARY KEY,"
    "  created TIMESTAMP(6) NOT NULL"
    ")"]])

(defmethod proto/init :jdbc
  [{:keys [url]}]
  (jdbc/with-db-connection [conn url]
    (doseq [migration migrations]
      (jdbc/with-db-transaction [conn conn]
        (jdbc/execute! conn (join " " migration) {:transaction? false}))))
  (->Store url))
