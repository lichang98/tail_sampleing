#!/bin/bash

# After build docker image, run as client processors and run the score process
# The scoring docker image is  registry.cn-hangzhou.aliyuncs.com/cloud_native_match/scoring:0.1

docker run --rm -it  --net host -e "SERVER_PORT=8000" --name "clientprocess1" -d $1
docker run --rm -it  --net host -e "SERVER_PORT=8001" --name "clientprocess2" -d $1
docker run --rm -it  --net host -e "SERVER_PORT=8002" --name "backendprocess" -d $1
docker run --rm --net host -e "SERVER_PORT=8081" --name scoring -d registry.cn-hangzhou.aliyuncs.com/cloud_native_match/scoring:0.1


