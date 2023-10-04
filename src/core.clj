(ns core
  (:gen-class)
  (:require [clo-web.server :as server]))

(defn -main []
  (server/start))