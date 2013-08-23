/**
* Provision Service Frontend Library
* @author Peter de Lange (lange@dbis.rwth-aachen.de)
*/

var PS = (function(PS){
	
	
	/**
	* The Provision Service Library
	* @return {Object}
	*/
	PS.ProvisionService = function(){
	
		//Private Properties
		var LAS2PEERHOST = "http://localhost:8080/";
		var LAS2PEERSERVICENAME = "i5.las2peer.services.monitoring.provision.MonitoringDataProvisionService";
		var LAS2PEERUSER = "PROVISION_SERVICE_FRONTEND";
		var LAS2PEERUSERPASS = "PSFP";
		
		var LAS2peerClient;
		var loginCallback = function(){};
		
		
		//Private Methods
		/**
		* Retrieves all available nodes.
		* @param callback Callback function, called when the result has been retrieved. An array of node names.
		*/
		var getNodes = function(callback){
			if(LAS2peerClient.getStatus() == "loggedIn"){
				LAS2peerClient.invoke(LAS2PEERSERVICENAME, "getNodes", [], function(status, result) {
					if(status == 200 || status == 204) {
						callback(result.value);
					} else {
						callback("Error! Message: " + result);
					}
				});
			}
		};
		
		/**
		* Retrieves all available services.
		* @param callback Callback function, called when the result has been retrieved. An array of service names.
		*/
		var getServices = function(callback){
			if(LAS2peerClient.getStatus() == "loggedIn"){
				LAS2peerClient.invoke(LAS2PEERSERVICENAME, "getServices", [], function(status, result) {
					if(status == 200 || status == 204) {
						callback(result.value);
					} else {
						callback("Error! Message: " + result);
					}
				});
			}
		};
		
		/**
		* Retrieves all available services.
		* @param callback Callback function, called when the result has been retrieved. An array of service names.
		*/
		var getSuccessModels = function(serviceName, callback){
			if(LAS2peerClient.getStatus() == "loggedIn"){
			
				var params = [],
				paramServiceName = {},
				paramUpdate = {};
				
				paramServiceName.type = "String";
				paramServiceName.value = serviceName;
				paramUpdate.type = "boolean";
				paramUpdate.value = true;
				
				params.push(paramServiceName,paramUpdate);
				
				LAS2peerClient.invoke(LAS2PEERSERVICENAME, "getModels", params, function(status, result) {
					if(status == 200 || status == 204) {
						callback(result.value);
					} else {
						callback("Error! Message: " + result);
					}
				});
			}
		};
		
		//Constructor
		LAS2peerClient = new LasAjaxClient("MonitoringDataProvisionService", function(statusCode, message) {
			switch(statusCode) {
				case Enums.Feedback.LoginSuccess:
					console.log("Login successful!");
					loginCallback();
					break;
				case Enums.Feedback.LogoutSuccess:
					console.log("Logout successful!");
					break;
				case Enums.Feedback.LoginError:
				case Enums.Feedback.LogoutError:
					console.log("Login error: " + statusCode + ", " + message);
					break;
				case Enums.Feedback.InvocationWorking:
				case Enums.Feedback.InvocationSuccess:
				case Enums.Feedback.Warning:
					break;
				case Enums.Feedback.PingSuccess:
					break;
				default:
					console.log("Unhandled Error: " + statusCode + ", " + message);
					break;
			}
		});
		
		
		//Public Methods
		return {
			
			/**
			* Logs in default Provision Service Frontend User.
			* @param callback Callback, called when user has been logged in successfully.
			*/
			login: function(callback){
				if(typeof callback == "function"){
					loginCallback = callback;
				}
				if(LAS2peerClient.getStatus() == "loggedIn"){
					loginCallback();
				} else {
					LAS2peerClient.login(LAS2PEERUSER, LAS2PEERUSERPASS, LAS2PEERHOST, "MonitoringDataProvisionService");
				}
			},
			
			/**
			* Retrieves all available nodes.
			* @param callback Callback function, called when the result has been retrieved. An array of node names.
			*/
			getNodes: function(callback){
				getNodes(callback);
			},
			
			/**
			* Retrieves all available services.
			* @param callback Callback function, called when the result has been retrieved. An array of service names.
			*/
			getServices: function(callback){
				getServices(callback);
			},
			
			/**
			* Retrieves all available success models for a given service name.
			* @param callback Callback function, called when the result has been retrieved. An array of success model names.
			*/
			getSuccessModels: function(serviceName, callback){
				getSuccessModels(serviceName, callback);
			}
		}
		
	};
	
	return PS;
	
})(PS || {});