(ns pivot-slack.db
  (:require [datahike.api :as d]
            [slingshot.slingshot :refer [try+ throw+]]
            [environ.core :refer [env]]))

(def uri (:db-uri env))

(def schema {:slack/user-id {:db/unique :db.unique/identity}
             :slack/email {:db/unique :db.unique/identity}
             :pivotal/email {:db/unique :db.unique/identity}
             :pivotal/user-id {:db/unique :db.unique/identity}
             ;; :essential/id {:db/unique :db.unique/identity}
             ;; :essential/ts {:db/index true}
             ;; :essential/owner
             ;; :essential/story-name
             ;; :essential/project-id
             })

(try+
 (d/create-database-with-schema uri schema)
 (catch [:type :db-already-exists] ex
   (prn "DB already exist.")
   ))

(def conn (d/connect uri))

(defn story-creation-in-progress [])


;; (prn (d/transact conn [{:db/id (d/tempid :db.part/user)
;;                         :slack/user-id "Ux123"
;;                         :slack/email "test@gmail.com"
;;                         :pivotal/user-id 1234
;;                         :pivotal/email "pivotal@gmail.com"
;;                         }]))

;; note: no d/touch

;; (d/pull @conn '[*] (:db/id (d/entity @conn [:slack/email "test@gmail.com"])))

;; (d/pull @conn '[*] (:db/id (d/entity @conn [:pivotal/email "lihster@gmail.com"])))

;; (d/pull @conn '[*] (:db/id (d/entity @conn [:slack/user-id "U7PBZQFAL"])))

;; (d/entity @conn [:slack/email "lihser@gmail.com"])
