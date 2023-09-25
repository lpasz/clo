;; (ns user
;;   (:require [ragtime.jdbc :as jdbc]
;;             [core :as core]
;;             [ragtime.repl :as rag]))

;; (def datastore (jdbc/sql-database core/pg-db))

;; (defn config []
;;   {:datastore  datastore
;;    :migrations (jdbc/load-directory "./migrations")})

;; (defn migrate []
;;   (rag/migrate (config)))

;; (defn rollback []
;;   (rag/rollback (config)))


;; (comment
;;   (migrate)
;;   (rollback))

