#!/bin/sh
set -x

/usr/lib/jvm/java-1.8-openjdk/bin/java -jar /opt/inventory/inventory.jar /opt/inventory/config/config.clj -XX:+CrashOnOutOfMemoryError
