(ns core
  (:gen-class)
  (:require
   [reitit.ring :as ring]
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
   [core :as core]
   [ragtime.repl :as rag]
   [clojure.pprint :as pprint])
  (:import [java.sql Date]))


;; Database

(def pg-db {:dbtype "postgresql"
            :port 5432
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
      (:generated_key)))

;; Migrations

(def datastore (jdbc/sql-database core/pg-db))

(defn config []
  {:datastore  datastore
   :migrations (jdbc/load-directory "./migrations")})

(defn migrate []
  (rag/migrate (config)))

(defn rollback []
  (rag/rollback (config)))


;; (defn max-n-characters [str n]
;;   (>= n (count str)))

;; (s/def ::stack (s/or :nil nil? :stack (s/coll-of (s/and string? #(max-n-characters % 32)))))

;; (defn created [{:keys [body-params]}]
;;   (resp/created "/pessoas/1"))


;; (comment
;; (defn created [{:keys [body-params]}]
;;   (try
;;     (let [data {:nome (:nome body-params),
;;                 :stack (if (s/valid? ::stack (:stack body-params))
;;                          (str/join " " (:stack body-params))
;;                          (throw (Exception. "Invalid Stack"))),
;;                 :apelido (:apelido body-params),
;;                 :nascimento (Date/valueOf (:nascimento body-params))}
;;           values (vals (select-keys data [:nome :stack :apelido]))
;;           data-with-search (assoc data :search (str/join " " values))]
;;       (insert {:insert-into [:pessoas]
;;                :values [data-with-search]})

;;       (resp/created "/pessoas/1"))
;;     (catch Exception _ (resp/status 422))))

;; (defn handler [_]
;;   (resp/created ""))

;; Router

(def router-config
  {:exception pretty/exception
   :data {:coercion reitit.coercion.spec/coercion
          :muuntaja m/instance
          :middleware [;; Put :query-params in the request, otherwise is just :query-string
                       parameters/parameters-middleware
                       ;; Not required for the example since we don't return bodies
                       ;;  muuntaja/format-response-middleware
                       ;; Required to parse body into :body-params
                       muuntaja/format-request-middleware
                       ;; Put :body-params in the request, otherwise is just :body
                       muuntaja/format-negotiate-middleware
                       (exception/create-exception-middleware
                        {::exception/default (partial exception/wrap-log-to-console
                                                      exception/default-handler)})]}})

(def app (ring/ring-handler
          (ring/router [["/pessoas" {:post (fn [_req]
                                            ;;  (clojure.pprint/pprint req)
                                             (resp/created "/pessoas/1"))
                                     :get (fn [_] (resp/status 200))}]
                        ["/pessoas/:id" {:get (fn [_] (resp/status 200))}]
                        ["/contagem-pessoas" {:get (fn [_] (resp/status 200))}]]
                       router-config)))

(defn start []
  (println "Jetty is starting...")
  (jetty/run-jetty #'app {:port 8081, :join? false})
  (println "Jetty is running..."))

(defn -main []
  ;; (u/migrate)
  (start))

(comment
  (start))
