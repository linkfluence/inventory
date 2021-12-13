FROM alpine:3.10.0

RUN apk --no-cache add openjdk11-jdk curl

RUN mkdir /opt/inventory && mkdir /opt/inventory/config && mkdir /opt/inventory/data

RUN curl -L https://github.com/linkfluence/inventory/releases/download/0.16.12/inventory-0.16.12-standalone.jar -o /opt/inventory/inventory.jar

COPY docker-entrypoint.sh /

RUN chmod +x /docker-entrypoint.sh

CMD [ "/docker-entrypoint.sh" ]
