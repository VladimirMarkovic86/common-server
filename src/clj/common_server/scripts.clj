(ns common-server.scripts
  (:require [common-server.scripts.language :as cssl]
            [common-server.scripts.role :as cssr]
            [common-server.scripts.user :as cssu]
            [common-server.scripts.preferences :as cssp]))

(defn initialize-db
  "Initialize database"
  []
  (cssl/insert-labels)
  (cssr/insert-roles)
  (cssu/insert-users)
  (cssp/insert-preferences))

