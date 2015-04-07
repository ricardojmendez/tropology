boot2docker start
$(boot2docker shellinit)
docker run -c 4 -i -t -d --name tropology-test --cap-add=SYS_RESOURCE -p 7373:7474 tpires/neo4j
