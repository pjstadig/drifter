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
  [options]
  (proto/init options))

(defn completed-migration-ids
  [store]
  (with-open [connection (proto/connect store)]
    (proto/completed-migration-ids connection)))

(defn migrate
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
