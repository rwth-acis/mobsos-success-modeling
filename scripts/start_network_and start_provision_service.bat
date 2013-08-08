cd ..
set BASE=%CD%
set CLASSPATH="%BASE%/lib/*;"

java -cp %CLASSPATH% i5.las2peer.testing.L2pNodeLauncher windows_shell -s 9010 - uploadStartupDirectory startService('i5.las2peer.services.monitoring.provision.MonitoringDataProvisionService','MDPSPass') interactive
pause