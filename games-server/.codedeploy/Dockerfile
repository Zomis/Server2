FROM openjdk:11-jre

EXPOSE 42638

WORKDIR /server2/
ADD *-all.jar /server2/

VOLUME /data/logs/

CMD java -jar /server2/games-server-1.0-SNAPSHOT-all.jar
