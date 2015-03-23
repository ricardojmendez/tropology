boot2docker start
$(boot2docker shellinit)
docker run -i -t -d --name neo4j --cap-add=SYS_RESOURCE -p 7373:7474 tpires/neo4j
