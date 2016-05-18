;;;; Copyright © Paul Stadig.  All rights reserved.
;;;;
;;;; This program and the accompanying materials are made available under the
;;;; terms of the Eclipse Public License v1.0 which accompanies this
;;;; distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
(ns wayfarer.core
  (:require
   [wayfarer.jdbc]
   [wayfarer.protocol :as proto]))

(defn init
  "Initializes a store against which you would like to run migrations.
  The :backend key will specify which backend to use (currently only :jdbc).
  The other options are backend specific.  For more information, please see the
  namespace docstrings for any of the backends.

  An example initialization map is:

  {:backend :jdbc
   :url \"jdbc:postgresql://localhost/wayfarer?user=wayfarer&password=wayfarer\"}"
  [{:keys [backend] :as options}]
  (proto/init options))

(defn completed-migration-ids
  "A set of the ids of all completed migrations.  The ids are strings and are
  expected to be a string representation of a
  UUID (e.g. \"ebbb9b5a-fc77-4b64-9e13-8caf4c17cd8f\")."
  [store]
  (set
   (with-open [connection (proto/connect store)]
     (proto/completed-migration-ids connection))))

(defn migrate
  "Connect to the store (which should have been created with init) and, in the
  order they are specified, execute all of the given migrations that have not
  already been executed."
  [store migrations]
  (with-open [connection (proto/connect store)]
    (let [completed-ids (proto/completed-migration-ids connection)
          migrations (remove (comp completed-ids str :id) migrations)]
      (doseq [migration migrations]
        (with-open [transaction (proto/start-transaction connection)]
          (try
            (proto/execute transaction migration)
            (proto/commit transaction)
            (catch Throwable t
              (proto/rollback transaction)
              (throw t))))))))
