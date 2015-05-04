boot2docker start
$(boot2docker shellinit)
docker run --name tropology-test-pg -e POSTGRES_PASSWORD=testdb -d -p 5432:5432 postgres

