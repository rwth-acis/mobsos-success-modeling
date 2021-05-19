package i5.las2peer.services.mobsos.successModeling.queryVisualizationService;

import i5.las2peer.api.Context;
import i5.las2peer.api.execution.ServiceInvocationException;

import java.io.Serializable;
import java.util.List;

public class QVConnector {
    private String serviceIdentifier;

    public QVConnector(String serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
    }

    public void grantUserAccessToDatabase(String databaseKey, SQLDatabaseType databaseTypeCode, String username,
                                          String password, String database, String host, Integer port) throws ServiceInvocationException {
        this.invokeMethodOnQVService("addDatabase", databaseKey, databaseTypeCode.getCode(), username, password,
                database, host, port);
    }

    public List<String> getDatabaseKeys() throws ServiceInvocationException {
        return (List<String>) this.invokeMethodOnQVService("getDatabaseKeys");
    }

    private Object invokeMethodOnQVService(String methodName, Serializable... args) throws ServiceInvocationException {
        return Context.get().invoke(this.serviceIdentifier, methodName, args);
    }

    /**
     * Copied from the QV service.
     */
    public enum SQLDatabaseType {
        // A DB2 database. Works with the "db2jcc-0.jar" +  "db2jcc_licence_cu-0.jar" archive.
        DB2(1),
        // mysqlConnectorJava-5.1.16.jar (MYSQL 5.1)
        MYSQL(2),
        // jaybird-2.1.6.jar
        FIREBIRD(3),
        // sqljdbc4.jar
        MSSQL(4),
        //postgresql-9.0-801.jdbc4.jar (POSTGRESQL 9)
        POSTGRESQL(5),
        // derbyclient.jar
        DERBY(6),
        // ojdbc14.jar (ORACLE 10.2)
        ORACLE(7);

        private final int code;

        SQLDatabaseType(int code) {
            this.code = code;
        }

        public static SQLDatabaseType getSQLDatabaseType(int code) {
            switch (code) {
                case 1:
                    // DB2
                    return SQLDatabaseType.DB2;
                case 2:
                    // MYSQL
                    return SQLDatabaseType.MYSQL;
                case 3:
                    // FIREBIRD
                    return SQLDatabaseType.FIREBIRD;
                case 4:
                    // Microsoft SQL Server
                    return SQLDatabaseType.MSSQL;
                case 5:
                    // POSTGRESQL
                    return SQLDatabaseType.POSTGRESQL;
                case 6:
                    // JavaDB/Derby
                    return SQLDatabaseType.DERBY;
                case 7:
                    // ORACLE
                    return SQLDatabaseType.ORACLE;
            }

            // not known...
            return null;
        }

        public int getCode() {
            return this.code;
        }

        public String getDriverName() {
            switch (this.code) {
                case 1:
                    // DB2
                    return "com.ibm.db2.jcc.DB2Driver";
                case 2:
                    // MYSQL
                    return "com.mysql.jdbc.Driver";
                case 3:
                    // FIREBIRD
                    return "org.firebirdsql.jdbc.FBDriver";
                case 4:
                    // Microsoft SQL Server
                    return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                case 5:
                    // POSTGRESQL
                    return "org.postgresql.Driver";
                case 6:
                    // JavaDB/Derby
                    return "org.apache.derby.jdbc.ClientDriver";
                case 7:
                    // ORACLE
                    return "oracle.jdbc.driver.OracleDriver";
            }
            // not found...
            return null;
        }

        public String getJDBCurl(String host, String database, int port) {
            String url = null;

            // add the url prefix
            switch (this.code) {
                case 1:
                    // DB2
                    url = "jdbc:db2://" + host + ":" + port + "/" + database;
                    break;
                case 2:
                    // MYSQL
                    url = "jdbc:mysql://" + host + ":" + port + "/" + database;
                    break;
                case 3:
                    // FIREBIRD
                    url = "jdbc:firebirdsql:" + host + "/" + port + ":" + database;
                    break;
                case 4:
                    // Microsoft SQL Server
                    // does a connect work? username and password...
                    url = "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + database + ";";
                    break;
                case 5:
                    // POSTGRESQL
                    url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
                    break;
                case 6:
                    // JavaDB/Derby
                    url = "jdbc:derby://" + host + ":" + port + "/" + database;
                    break;
                case 7:
                    // ORACLE
                    url = "jdbc:oracle:thin:@" + host + ":" + port + ":" + database;
                    break;
                default:
                    // not found...
                    return null;
            }


            return url;
        }
    }
}
