#!/bin/bash

SCRIPT_PATH=$(dirname $(readlink -f $0))
CLASSPATH="$SCRIPT_PATH/../lib/*"

uuid=$1
timestamp=$2

java -cp "$CLASSPATH" dk.statsbiblioteket.broadcasttranscoder.Cleanup \
 --hibernate_configfile=$SCRIPT_PATH/../conf/bta.iapetus.hibernate.cfg.xml\
 --infrastructure_configfile=$SCRIPT_PATH/../conf/bta.infrastructure.properties \
 --behavioural_configfile=$SCRIPT_PATH/../conf/bta.behaviour.properties \
 --programpid=$uuid \
 --timestamp=$timestamp