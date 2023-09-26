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
            [clojure.string :as str]
            [clojure.pprint :as pprint])
  (:import [java.sql Date]))


;; Database

(def pg-db {:dbtype "postgresql"
            :port 5444
            :user "postgres"
            :dbname "postgres"
            :password "postgres"})

(defn query [q]
  (->> q
       (sql/format)
       (j/query pg-db)))

(defn insert [q]
  (-> pg-db
      (j/execute! (sql/format q) {:return-keys ["id"]})
      (:id)))

;; Migrations

(def datastore (jdbc/sql-database core/pg-db))

(defn config []
  {:datastore  datastore
   :migrations (jdbc/load-directory "./migrations")})

(defn migrate []
  (rag/migrate (config)))

(defn rollback []
  (rag/rollback (config)))

;; Handlers

(defn max-n-characters [str n]
  (>= n (count str)))

(s/def ::stack (s/or :nil nil? :stack (s/coll-of (s/and string? #(max-n-characters % 32)))))


(defn created [{:keys [body-params]}]
  (println "create start 1")
  (clojure.pprint/pprint body-params)
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

      (println id)
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
      (query)
      (first)
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

(defn start []
  (println "Jetty is starting...")
  (jetty/run-jetty #'app {:port 8081, :join? false})
  (println "Jetty is running..."))

(defn -main []
  (migrate)
  (start))
