FROM openjdk:8-jdk-alpine

ENV HTTP_PORT=8080
ENV HTTPS_PORT=8443
ENV LAS2PEER_PORT=9011
ENV CHART_API_ENDPOINT=http://127.0.0.1:3000
ENV GRAPHQ_HOST=127.0.0.1:8090

RUN apk add --update bash xmlstarlet mysql-client apache-ant tini curl && rm -f /var/cache/apk/*
RUN addgroup -g 1000 -S las2peer && \
    adduser -u 1000 -S las2peer -G las2peer

COPY --chown=las2peer:las2peer . /src
WORKDIR /src

RUN dos2unix docker-entrypoint.sh
RUN  dos2unix etc/i5.las2peer.services.mobsos.successModeling.MonitoringDataProvisionService.properties
# run the rest as unprivileged user
USER las2peer
RUN ant jar

EXPOSE $HTTP_PORT
EXPOSE $HTTPS_PORT
EXPOSE $LAS2PEER_PORT
ENTRYPOINT ["/sbin/tini", "--", "/src/docker-entrypoint.sh"]
