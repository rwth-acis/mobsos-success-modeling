cd ..
set BASE=%CD%
set CLASSPATH="%BASE%/lib/*;"

java -cp %CLASSPATH% i5.las2peer.testing.L2pNodeLauncher -w -p 9011 uploadStartupDirectory('etc/startup') startService('i5.las2peer.services.monitoring.provision.MonitoringDataProvisionService','MDPSPass') startHttpConnector interactive
pause