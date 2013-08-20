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
			}
		
		}
	
	};
	
	return PS;
	
})(PS || {});