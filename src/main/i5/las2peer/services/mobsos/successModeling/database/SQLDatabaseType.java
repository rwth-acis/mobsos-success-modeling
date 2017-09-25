package i5.las2peer.services.mobsos.successModeling.database;

/**
 *
 * Enumeration class that provides the right drivers according to the database type. The original code was taken from
 * the QueryVisualizationService.
 *
 * This implementation only supports DB2 and MySQL, since those are the ones that were tested with this service.
 *
 * @author Peter de Lange
 *
 */
public enum SQLDatabaseType {

	/**
	 * A DB2 database. Works with the "db2jcc-0.jar" + "db2jcc_licence_cu-0.jar" archive.
	 */
	DB2(1),

	/**
	 * A MySQL 5.1 database. Works with the "mysqlConnectorJava-5.1.16.jar" archive.
	 */
	MySQL(2);

	private final int code;

	SQLDatabaseType(int code) {
		this.code = code;
	}

	/**
	 *
	 * Returns the code of the database.
	 *
	 * @return a code
	 *
	 */
	public int getCode() {
		return this.code;
	}

	/**
	 *
	 * Returns the database type.
	 *
	 * @param code the number corresponding to a database type
	 *
	 * @return the corresponding {@link SQLDatabaseType} representation
	 *
	 */
	public static SQLDatabaseType getSQLDatabaseType(int code) {
		switch (code) {
		case 1:
			return SQLDatabaseType.DB2;
		case 2:
			return SQLDatabaseType.MySQL;
		}
		return null;
	}

	/**
	 *
	 * Returns the driver name of the corresponding database. The library of this driver has to be in the "lib" folder.
	 *
	 * @return a driver name
	 *
	 */
	public String getDriverName() {
		switch (this.code) {
		case 1:
			return "com.ibm.db2.jcc.DB2Driver";
		case 2:
			return "com.mysql.cj.jdbc.Driver";
		}
		return null;
	}

	/**
	 *
	 * Constructs a URL prefix that can be used for addressing a database.
	 *
	 * @param host a database host address
	 * @param database the database name
	 * @param port the port the database is running at
	 *
	 * @return a String representing the URL prefix
	 *
	 */
	public String getURLPrefix(String host, String database, int port) {
		String url = null;
		switch (this.code) {
		case 1:
			url = "jdbc:db2://" + host + ":" + port + "/" + database;
			break;
		case 2:
			url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC";
			break;
		default:
			return null;
		}
		return url;
	}

}
