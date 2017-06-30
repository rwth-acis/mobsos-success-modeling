MobSOS Success-Modeling
===========================================
[![Build Status](https://travis-ci.org/rwth-acis/mobsos-success-modeling.svg?branch=master)](https://travis-ci.org/rwth-acis/mobsos-success-modeling) [![codecov](https://codecov.io/gh/rwth-acis/mobsos-success-modeling/branch/master/graph/badge.svg)](https://codecov.io/gh/rwth-acis/mobsos-success-modeling)

This service is part of the MobSOS monitoring concept and provides visualization functionality of the monitored data to the web-frontend.

Database
--------
The Success-Modeling service uses the database with the monitored data. If you need to set up the database have a look at the [MobSOS Data-Processing service](mobsos-data-processing).
* [MySQL](https://github.com/rwth-acis/mobsos-data-processing/blob/master/bin/create_database_MySQL.sql) 
* [DB2](https://github.com/rwth-acis/mobsos-data-processing/blob/master/bin/create_database_DB2.sql)

After that configure the [property](etc/i5.las2peer.services.mobsos.successModeling.MonitoringDataProvisionService.properties) file of the service and enter your database credentials.

```INI
databaseTypeInt = 2
databaseUser = exampleuser
databasePassword = examplepass
databaseName = exampledb
databaseHost = localhost
databasePort = 3306
useFileService = FALSE
catalogFileLocation = measure_catalogs/
successModelsFolderLocation = success_models/
```

The [las2peer-FileService](https://github.com/rwth-acis/las2peer-FileService) can be used for users to upload their own catalogs and success models if the feature is disabled the files are loaded from the filesystem. The paths are needed in both cases. 


Build
--------
Execute the following command on your shell:

```shell
ant all 
```

Start
--------

To start the MobSOS Success-Modeling service, use one of the available start scripts:

Windows:

```shell
bin/start_network.bat
```

Unix/Mac:
```shell
bin/start_network.sh
```

--------
Have a look at the [manual](../../wiki/Manual) if you need information about success models, measure catalogs and the different visualization types.
