#!/bin/bash

# this script starts a las2peer node providing the example service of this project
# pls execute it from the root folder of your deployment, e. g. ./bin/start_network.sh

#java -cp "lib/*" i5.las2peer.tools.L2pNodeLauncher -p 9011 -b 192.168.0.13:9010 -o -s ./service/ "uploadStartupDirectory" "startService('i5.las2peer.services.mobsos.successModeling.MonitoringDataProvisionService@0.7.0')" startWebConnector interactive
java -cp "lib/*" i5.las2peer.tools.L2pNodeLauncher -p 9011 -o -s ./service/ "uploadStartupDirectory" "startService('i5.las2peer.services.mobsos.successModeling.MonitoringDataProvisionService@0.7.0')" startWebConnector interactive
