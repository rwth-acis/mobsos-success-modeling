cd ..
set BASE=%CD%
set CLASSPATH="%BASE%/lib/*;"

java -cp %CLASSPATH% i5.las2peer.tools.L2pNodeLauncher -b 172.30.160.1:9011 -p 9013 uploadStartupDirectory('etc/startup') startService('i5.las2peer.services.monitoring.provision.MonitoringDataProvisionService','MDPSPass') interactive
pause