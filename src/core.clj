(ns core
  (:gen-class)
  (:require [reitit.ring :as ring]
            [ring.util.response :as resp]
            [ring.adapter.jetty :as jetty]
            [clojure.java.jdbc :as j]
            [honey.sql :as sql]
            [clojure.spec.alpha :as s]
            [reitit.coercion.spec]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :as rag]
            [clojure.string :as str])
  (:import [java.sql Date]))


;; Database

(def pg-uri (or (System/getenv "POSTGRES_URL")
                "postgres://postgres:postgres@postgres:5432/postgres"))

pg-uri

(defn query [q]
  (->> q
       (sql/format)
       (j/query pg-uri)))

(defn one [q]
  (first (query q)))

(defn insert [q]
  (-> pg-uri
      (j/execute! (sql/format q) {:return-keys ["id"]})
      (:id)))

;; Migrations

(def datastore (jdbc/sql-database pg-uri))

(defn config []
  {:datastore  datastore
   :migrations (jdbc/load-directory "./migrations")})

(defn migrate []
  (try
    (println "starting migrations")
    (println pg-uri)
    (println (j/query pg-uri ["SELECT current_database()"]))
    (rag/migrate (config))
    (println "finished migrations")
    (catch Exception _ (println "error running migrations"))))


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
