java -Xms265m -Xmx2048m -Ddb.url=http://neo4j:testneo4j@localhost:7474/db/data/ -Dupdate.cron="0 * /9 * * * *" -Dupdate.size=2 -cp target/tropefest.jar clojure.main -m tropefest.core
