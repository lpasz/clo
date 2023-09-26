(ns core
  (:gen-class)
  (:require [clojure.java.jdbc :as j]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [honey.sql :as sql]
            [muuntaja.core :as m]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :as rag]
            [reitit.coercion.spec]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [ragtime.strategy :as rs]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [hikari-cp.core :as hcp]
            [ring.util.response :as resp])
  (:import [java.sql Date]))


;; Database

(def postgres-url (or (System/getenv "POSTGRES_URL")
                      "postgres://postgres:postgres@localhost:5432/postgres"))

(def db-uri (java.net.URI. postgres-url))

(defn datasource-options []
  (let [[username password] (str/split (or (.getUserInfo db-uri) ":") #":")]
    {:username           (or username (System/getProperty "user.name"))
     :password           (or password "")
     :port-number        (.getPort db-uri)
     :database-name      (str/replace-first (.getPath db-uri) "/" "")
     :server-name        (.getHost db-uri)
     :auto-commit        true
     :read-only          false
     :adapter            "postgresql"
     :connection-timeout 30000
     :validation-timeout 5000
     :idle-timeout       600000
     :max-lifetime       1800000
     :minimum-idle       10
     :maximum-pool-size  20
     :pool-name          (str "db-pool" (java.util.UUID/randomUUID))
     :register-mbeans    false}))

(def db-conn
  (delay {:datasource (hcp/make-datasource (datasource-options))}))

(defn query [q]
  (->> q
       (sql/format)
       (j/query @db-conn)))

(defn one [q]
  (first (query q)))

(defn insert [q]
  (-> @db-conn
      (j/execute! (sql/format q) {:return-keys ["id"]})
      (:id)))

;; Migrations

(defn config []
  {:datastore  (jdbc/sql-database (System/getenv "POSTGRES_URL"))
   :migrations (jdbc/load-directory "./migrations")
   :strategy rs/rebase})

(defn migrate []
  (rag/migrate (config)))

(defn rollback []
  (rag/rollback (config)))

;; Handlers

(defn max-n-characters [str n]
  (>= n (count str)))

(s/def ::tech (s/coll-of (s/and string? #(max-n-characters % 32))))
(s/def ::stack (s/or :nil nil?
                     :stack ::tech))


(defn created [{:keys [body-params]}]
  (try
    (let [data {:nome (:nome body-params),
                :stack (if (s/valid? ::stack (:stack body-params))
                         (str/join " " (:stack body-params))
                         (throw (Exception. "Invalid Stack"))),
                :apelido (:apelido body-params),
                :nascimento (Date/valueOf (:nascimento body-params))}
          values (-> data (select-keys [:nome :stack :apelido]) (vals))
          search (str/join " " values)
          data (assoc data :search search)
          id (insert {:insert-into [:pessoas] :values [data]})
          location (str "/pessoas/" id)]
      (resp/created location))
    (catch Exception _ (resp/status 422))))



(defn search-term [{:keys [query-params]}]
  (if-let [term (query-params "t")]
    (->> {:select [:id :apelido :nome :nascimento :stack]
          :from :pessoas
          :where [:ilike :search (str "%" term "%")]}
         query
         resp/response)
    (resp/status 400)))

(defn search-id [{:keys [path-params]}]
  (if-let [result (->> {:select [:id :apelido :nome :nascimento :stack]
                        :limit 1
                        :from :pessoas
                        :where [:= :id (Integer/parseInt (:id path-params))]}
                       (query)
                       (first))]
    (resp/response result)
    (resp/status 404)))

(defn count-users [_]
  (-> {:select [[:%count.*]]
       :from :pessoas}
      (one)
      (:count)
      (str)
      (resp/response)))

;; Router

(def router-config
  {:exception pretty/exception
   :data {:coercion reitit.coercion.spec/coercion
          :muuntaja m/instance
          :middleware [;; Put :query-params in the request, otherwise is just :query-string
                       parameters/parameters-middleware
                       ;; Put :body-params in the request, otherwise is just :body
                       muuntaja/format-negotiate-middleware
                       ;; Not required for the example since we don't return bodies
                       muuntaja/format-response-middleware
                       ;; Required to parse body into :body-params
                       muuntaja/format-request-middleware
                       (exception/create-exception-middleware
                        {::exception/default (partial exception/wrap-log-to-console
                                                      exception/default-handler)})]}})

(def app (ring/ring-handler
          (ring/router [["/pessoas" {:post created
                                     :get search-term}]
                        ["/pessoas/:id" {:get search-id}]
                        ["/contagem-pessoas" {:get count-users}]]
                       router-config)))

(def server-port (Integer/parseInt (or (System/getenv "SERVER_PORT") "8080")))

(defn start []
  (println (str "Jetty is starting in " server-port "..."))
  (jetty/run-jetty #'app {:port server-port, :join? false})
  (println (str "Jetty is running on " server-port "...")))

(defn -main []
  (migrate)
  (start))
