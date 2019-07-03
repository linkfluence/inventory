FROM alpine:3.10.0

RUN apk --no-cache add openjdk8-jre

RUN mkdir /opt/inventory && mkdir /opt/inventory/config && mkdir /opt/inventory/data

COPY target/uberjar/inventory-*-standalone.jar /opt/inventory/inventory.jar

CMD ["java -jar /opt/inventory/inventory.jar /opt/inventory/config/congig.clj"]
