(ns clo.db
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as str]
            [hikari-cp.core :as hcp]
            [honey.sql :as sql]
            [reitit.coercion.spec]))

;; Database Config with Hikari connection pool

(def postgres-url (or (System/getenv "POSTGRES_URL")
                      "postgres://clo_user:clo_password@localhost:5432/clo_db"))

(def db-uri (java.net.URI. postgres-url))

(defn datasource-options []
  (let [[username password] (str/split (or (.getUserInfo db-uri) ":") #":")]
    {:username           username
     :password           password
     :port-number        (.getPort db-uri)
     :database-name      (str/replace-first (.getPath db-uri) "/" "")
     :server-name        (.getHost db-uri)
     :auto-commit        true
     :read-only          false
     :adapter            "postgresql"
     :connection-timeout 60000
     :validation-timeout 5000
     :idle-timeout       600000
     :max-lifetime       1800000
     :minimum-idle       50
     :maximum-pool-size  100
     :pool-name          (str "db-pool" (java.util.UUID/randomUUID))
     :register-mbeans    false}))


(def db-conn
  (delay {:datasource (hcp/make-datasource (datasource-options))}))

;; Database query/insert functions with honeysql

(defn query [q]
  (->> q
       (sql/format)
       (j/query @db-conn)))

(defn one [q]
  (-> q query first))

(defn insert [q]
  (->> q
       (sql/format)
       (j/execute! @db-conn)))
