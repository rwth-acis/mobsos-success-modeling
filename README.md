# MobSOS Success-Modeling

[![Build Status](https://travis-ci.org/rwth-acis/mobsos-success-modeling.svg?branch=master)](https://travis-ci.org/rwth-acis/mobsos-success-modeling) [![codecov](https://codecov.io/gh/rwth-acis/mobsos-success-modeling/branch/master/graph/badge.svg)](https://codecov.io/gh/rwth-acis/mobsos-success-modeling) [![Join the chat at https://gitter.im/rwth-acis/mobsos](https://badges.gitter.im/rwth-acis/mobsos.svg)](https://gitter.im/rwth-acis/mobsos?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This service is part of the MobSOS monitoring concept and provides visualization functionality of the monitored data to the web-frontend. Have look at the Wiki to see how you can create your own success models.

## Database

The Success-Modeling service uses the database with the monitored data. If you need to set up the database have a look at the [MobSOS Data-Processing service](mobsos-data-processing).

- [MySQL](https://github.com/rwth-acis/mobsos-data-processing/blob/master/bin/create_database_MySQL.sql)
- [DB2](https://github.com/rwth-acis/mobsos-data-processing/blob/master/bin/create_database_DB2.sql)

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

## Build

Execute the following command on your shell:

```shell
./gradlew clean build --info
```

## Start

To start the MobSOS Success-Modeling service, use one of the available start scripts:

Windows:

```shell
bin/start_network.bat
```

Unix/Mac:

```shell
bin/start_network.sh
```

---

Have a look at the [manual](../../wiki/Manual) if you need information about success models, measure catalogs and the different visualization types.

## How to run using Docker

First build the image:

```bash
docker build . -t mobsos-success-modeling
```

Then you can run the image like this:

```bash
docker run -e MYSQL_USER=myuser -e MYSQL_PASSWORD=mypasswd -p 8080:8080 -p 9011:9011 mobsos-success-modeling
```

Replace _myuser_ and _mypasswd_ with the username and password of a MySQL user with access to a database named _LAS2PEERMON_.
The initial database setup must be performed by the [mobsos-data-processing](https://github.com/rwth-acis/mobsos-data-processing) container, which must be connected to the same database server.
By default the database host is _mysql_ and the port is _3306_.
The REST-API will be available via _http://localhost:8080/mobsos-success-modeling_ and the las2peer node is available via port 9011.

In order to customize your setup you can set further environment variables.

### Node Launcher Variables

Set [las2peer node launcher options](https://github.com/rwth-acis/las2peer-Template-Project/wiki/L2pNodeLauncher-Commands#at-start-up) with these variables.
The las2peer port is fixed at _9011_.

| Variable           | Default    | Description                                                                                                                                  |
| ------------------ | ---------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| BOOTSTRAP          | unset      | Set the --bootstrap option to bootrap with existing nodes. The container will wait for any bootstrap node to be available before continuing. |
| SERVICE_PASSPHRASE | processing | Set the second argument in _startService('<service@version>', '<SERVICE_PASSPHRASE>')_.                                                      |
| SERVICE_EXTRA_ARGS | unset      | Set additional launcher arguments. Example: `--observer` to enable monitoring.                                                               |

### Service Variables

See [database](#Database) for a description of the settings.

| Variable                       | Default           |
| ------------------------------ | ----------------- |
| MYSQL_USER                     | _mandatory_       |
| MYSQL_PASSWORD                 | _mandatory_       |
| MYSQL_HOST                     | mysql             |
| MYSQL_PORT                     | 3306              |
| USE_FILE_SERVICE               | FALSE             |
| CATALOG_FILE_LOCATION          | measure_catalogs/ |
| SUCCESS_MODELS_FOLDER_LOCATION | success_models/   |

### Web Connector Variables

Set [WebConnector properties](https://github.com/rwth-acis/las2peer-Template-Project/wiki/WebConnector-Configuration) with these variables.
_httpPort_ and _httpsPort_ are fixed at _8080_ and _8443_.

| Variable                             | Default                                                             |
| ------------------------------------ | ------------------------------------------------------------------- |
| START_HTTP                           | TRUE                                                                |
| START_HTTPS                          | FALSE                                                               |
| SSL_KEYSTORE                         | ""                                                                  |
| SSL_KEY_PASSWORD                     | ""                                                                  |
| CROSS_ORIGIN_RESOURCE_DOMAIN         | \*                                                                  |
| CROSS_ORIGIN_RESOURCE_MAX_AGE        | 60                                                                  |
| ENABLE_CROSS_ORIGIN_RESOURCE_SHARING | TRUE                                                                |
| OIDC_PROVIDERS                       | https://api.learning-layers.eu/o/oauth2,https://accounts.google.com |

### Other Variables

| Variable | Default | Description                                                                |
| -------- | ------- | -------------------------------------------------------------------------- |
| DEBUG    | unset   | Set to any value to get verbose output in the container entrypoint script. |

### Volumes

The following places should be persisted in volumes in productive scenarios:

| Path              | Description                            |
| ----------------- | -------------------------------------- |
| /src/node-storage | Pastry P2P storage.                    |
| /src/etc/startup  | Service agent key pair and passphrase. |
| /src/log          | Log files.                             |
