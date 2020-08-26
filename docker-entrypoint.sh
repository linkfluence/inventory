#!/bin/sh
set -x

/usr/lib/jvm/java-11-openjdk/bin/java -XX:OnOutOfMemoryError="kill -9 %p" -jar /opt/inventory/inventory.jar /opt/inventory/config/config.clj
