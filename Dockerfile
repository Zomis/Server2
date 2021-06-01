FROM openjdk:11-jre

EXPOSE 42638

WORKDIR /server2/
ADD games-server/build/libs/*-all.jar /server2/
ADD server2.conf.docker /server2/server2.conf

VOLUME /data/logs/

CMD java -jar /server2/games-server-1.0-SNAPSHOT-all.jar
