(ns core
  (:gen-class)
  (:require [clojure.java.jdbc :as j]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [hikari-cp.core :as hcp]
            [honey.sql :as sql]
            [muuntaja.core :as m]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :as rag]
            [ragtime.strategy :as rs]
            [reitit.coercion.spec]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as resp])
  (:import [java.sql Date]))

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
     :connection-timeout 30000
     :validation-timeout 5000
     :idle-timeout       600000
     :max-lifetime       1800000
     :minimum-idle       10
     :maximum-pool-size  50
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
  (first (query q)))

(defn insert [q]
  (-> @db-conn
      (j/execute! (sql/format q) {:return-keys ["id"]})
      (:id)))

;; Context 

(defn max-n-characters [str n]
  (>= n (count str)))


;; spec to check stack
(s/def ::stack (s/or :nil nil? :stack-coll (s/coll-of (s/and string? #(max-n-characters % 32)))))

(defn uuid [] (java.util.UUID/randomUUID))

(defn parse-stack [{:keys [stack]}]
  (if (s/valid? ::stack stack)
    (->> stack
         (filter not-empty)
         (str/join ";"))
    (throw (Exception. "Invalid Stack"))))

(defn parse-nascimento [{:keys [nascimento]}]
  (Date/valueOf nascimento))

(defn parse-search-term [{:keys [nome stack apelido]}]
  (str/join ";" [nome stack apelido]))

(defn prepare-pessoa [body-params]
  (merge body-params
         {:id (uuid)
          :stack (parse-stack body-params)
          :nascimento (parse-nascimento body-params)
          :search (parse-search-term body-params)}))

(defn create-pessoa [body-params]
  (let [data (prepare-pessoa body-params)]
    (insert {:insert-into [:pessoas]
             :values [data]})))

(defn pessoa-by-search-term [term]
  (-> {:select [:id :apelido :nome :nascimento :stack]
       :from :pessoas
       :where [:ilike :search (str "%" term "%")]}
      (query)))

(defn pessoa-by-id [id]
  (->> {:select [:id :apelido :nome :nascimento :stack]
        :limit 1
        :from :pessoas
        :where [:= :id id]}
       (one)))

;; Handlers

(defn created [{:keys [body-params]}]
  (try
    (let [id (create-pessoa body-params)
          location (str "/pessoas/" id)]
      (resp/created location))
    (catch Exception _ (resp/status 422))))


(defn search-id [{:keys [path-params]}]
  (if-let [id (Integer/parseInt (:id path-params))]
    (-> id
        (pessoa-by-id)
        (resp/response))
    (resp/status 404)))

(defn search-term [{:keys [query-params]}]
  (if-let [term (query-params "t")]
    (-> term
        (pessoa-by-search-term)
        (resp/response))
    (resp/status 400)))

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

(def jetty-server (delay (jetty/run-jetty #'app {:port server-port, :join? false})))

(defn start []
  (println (str "Jetty is starting in " server-port "..."))
  @jetty-server
  (println (str "Jetty is running on " server-port "...")))

;; Migrations

(defn config []
  {:datastore  (jdbc/sql-database postgres-url)
   :migrations (jdbc/load-directory "./migrations")
   :strategy rs/rebase})

(defn migrate []
  (try
    (rag/migrate (config))
    (catch Exception _ nil)))

(defn rollback []
  (rag/rollback (config)))

(defn -main []
  ;; Done in a single connection before we start the connection pool
  (migrate)
  ;; Start web server
  (start))
