(ns clo-web.server
  (:require [ring.adapter.jetty :as jetty]
            [clo-web.router :refer [router]]))

(def server-port (Integer/parseInt (or (System/getenv "SERVER_PORT") "8080")))

(def jetty-server (delay (jetty/run-jetty #'router {:port server-port, :join? false})))

(defn start []
  @jetty-server)

