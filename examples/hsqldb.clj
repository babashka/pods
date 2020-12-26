(require '[babashka.pods :as pods])

(pods/load-pod 'org.babashka/hsqldb "0.0.1")

(require '[pod.babashka.hsqldb :as db])

(def db "jdbc:hsqldb:mem:testdb;sql.syntax_mys=true")

(db/execute! db ["create table foo ( foo int );"])

(db/execute! db ["insert into foo values (1, 2, 3);"])

(db/execute! db ["select * from foo;"])
