(ns core
  (:require [clo-web.server :as server])
  (:gen-class))

(defn -main []
  (server/start))