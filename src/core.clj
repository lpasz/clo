(ns core
  (:gen-class)
  (:require
   [reitit.ring :as ring]
   [ring.util.response :as resp]
   [ring.adapter.jetty :as jetty]
  ;;  [clojure.java.jdbc :as j]
  ;;  [honey.sql :as sql]
  ;;  [clojure.spec.alpha :as s]
  ;;  [reitit.coercion.spec]
  ;;  [reitit.ring.coercion :as coercion]
  ;;  [reitit.dev.pretty :as pretty]
  ;;  [reitit.ring.middleware.muuntaja :as muuntaja]
  ;;  [reitit.ring.middleware.exception :as exception]
  ;;  [reitit.ring.middleware.multipart :as multipart]
  ;;  [reitit.ring.middleware.parameters :as parameters]
  ;;  [muuntaja.core :as m]
  ;;  [clojure.string :as str]
  ;;  [clojure.pprint :as pprint]
  ;;  [clojure.string :as string]
            ;; [user :as u]

   [clojure.pprint :as pprint])
  ;; (:import [java.sql Date])
  )


;; (def router-config
;;   {:exception pretty/exception
;;    :data {:coercion reitit.coercion.spec/coercion
;;           :muuntaja m/instance
;;           :middleware [;;  parameters/parameters-middleware
;;                       ;;  muuntaja/format-negotiate-middleware
;;                       ;;  muuntaja/format-response-middleware
;;                       ;;  (exception/create-exception-middleware
;;                       ;;   {::exception/default (partial exception/wrap-log-to-console
;;                       ;;                                 exception/default-handler)})
;;                       ;;  muuntaja/format-request-middleware
;;                       ;;  coercion/coerce-response-middleware
;;                       ;;  coercion/coerce-request-middleware
;;                       ;;  multipart/multipart-middleware
;;                        ]}})

;; (def pg-db {:dbtype "postgresql"
;;             :port 5432
;;             :user "postgres"
;;             :dbname "postgres"
;;             :password "postgres"})

;; (defn query [q]
;;   (->> q
;;        (sql/format)
;;        (j/query pg-db)))

;; (defn insert [q]
;;   (-> pg-db
;;       (j/execute! (sql/format q) {:return-keys ["id"]})
;;       (:generated_key)))

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

(def app (ring/ring-handler
          (ring/router [["/pessoas" {:post (fn [_] (resp/created "/pessoas/1"))
                                     :get (fn [_] (resp/status 200))}]
                        ["/pessoas/:id" {:get (fn [_] (resp/status 200))}]
                        ["/contagem-pessoas" {:get (fn [_] (resp/status 200))}]]
                      ;;  router-config
                       )))

(defn start []
  (println "Jetty is starting...")
  (jetty/run-jetty #'app {:port 8080, :join? false})
  (println "Jetty is running..."))

(defn -main []
  ;; (u/migrate)
  (start))

(comment
  (start))
