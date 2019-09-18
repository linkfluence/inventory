#!/bin/sh
set -x

/usr/lib/jvm/java-1.8-openjdk/bin/java -XX:OnOutOfMemoryError="kill -9 %p" -jar /opt/inventory/inventory.jar /opt/inventory/config/config.clj
