cd %~p0
cd ..
set BASE=%CD%
set CLASSPATH="%BASE%/lib/*;"

java -cp %CLASSPATH% i5.las2peer.tools.L2pNodeLauncher  -p 9013 --service-directory service uploadStartupDirectory startService('i5.las2peer.services.mobsos.successModeling.MonitoringDataProvisionService@0.8.4','MDPSPass') startWebConnector interactive
pause
