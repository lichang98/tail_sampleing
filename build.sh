#!/bin/bash

cd "middle1"
ls
mvn clean package
cp target/middle1-1.0-SNAPSHOT.jar ..

cd "../middle2"
ls
mvn clean package
cp target/middle2-1.0-SNAPSHOT.jar ..

cd "../backend"
ls
mvn clean package
cp target/backend-1.0-SNAPSHOT.jar ..


