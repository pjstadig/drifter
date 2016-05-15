;;;; Copyright © Paul Stadig.  All rights reserved.
;;;;
;;;; This program and the accompanying materials are made available under the
;;;; terms of the Eclipse Public License v1.0 which accompanies this
;;;; distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
(ns wayfarer.protocol)

(defmulti init :backend)

(defprotocol IStore
  (connect [this]))

(defprotocol IConnection
  (completed-migration-ids [this])
  (start-transaction [this]))

(defprotocol ITransaction
  (execute [this migration])
  (rollback [this])
  (commit [this]))
