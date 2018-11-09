(ns pivot-slack.db
  (:require [datahike.api :as d])
  )

(def uri "datahike:file:///Users/lih/Documents/workspace/pivot-slack")

(def schema {:slack/user-id {:db/unique :db.unique/identity}
             :slack/email {:db/unique :db.unique/identity}
             :pivotal/email {:db/unique :db.unique/identity}
             :pivotal/user-id {:db/unique :db.unique/identity}})

(comment (d/create-database-with-schema uri schema))

(def conn (d/connect uri))

;; (prn (d/transact conn [{:db/id (d/tempid :db.part/user)
;;                         :slack/user-id "Ux123"
;;                         :slack/email "test@gmail.com"
;;                         :pivotal/user-id 1234
;;                         :pivotal/email "pivotal@gmail.com"
;;                         }]))

;; note: no d/touch

;; (d/pull @conn '[*] (:db/id (d/entity @conn [:slack/email "test@gmail.com"])))

;; (d/entity @conn [:slack/email "test2@gmail.com"])
