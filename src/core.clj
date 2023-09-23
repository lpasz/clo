(ns core
  (:require [reitit.ring :as ring]
            [reitit.core :as r]
            [ring.adapter.jetty :as jetty]
            [clojure.java.jdbc :as j])
  (:gen-class))

(def pg-db {:dbtype "postgresql"
            :port 5432
            :user "postgres"
            :dbname "postgres"
            :password "postgres"})

(defn created [_] {:status 201})
(defn handler [_] {:status 200})


(def router
  (ring/router [["/pessoas" {:post created
                             :get handler}]
                ["/pessoas/:id" {:get handler}]
                ["/contagem-pessoas" {:get handler}]]))

(def app (ring/ring-handler router))

(defn start []
  (jetty/run-jetty #'app {:port 80, :join? false}))

(defn -main [] (start))

(comment
  (j/query pg-db ["SELECT * FROM pessoas"])
  (r/match-by-path router "/pessoas")
  (r/match-by-path router "")
  (app {:request-method :post :uri "/api/shorty" :body {:url "www.google.com/api"}})
  (app {:request-method :get :uri "/pessoas" :query-string "t=macaco"}))