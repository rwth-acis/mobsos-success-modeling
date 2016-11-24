/**
* Provision Service Frontend Library
* 
* @author Peter de Lange (lange@dbis.rwth-aachen.de)
*/

function showUserInformation(){
		$('#user-information')[0].innerHTML = ""
		$("<las2peer-user-widget login-oidc-provider='https://api.learning-layers.eu/o/oauth2' login-oidc-token='"+window.localStorage["access_token"]+"'></las2peer-user-widget>").appendTo('#user-information');
	
}

function updateFileUpload(){
		$("#uploadButton").css("margin-left", "5px").css("display","inline").css("font-size","1em");
		$('#fu')[0].innerHTML = ""
		$(" <las2peer-file-upload-widget id='uploadwidget' upload-success-callback='uploadSuccess' base-url='http://localhost:8080/' login-oidc-provider='https://api.learning-layers.eu/o/oauth2' login-oidc-token='"+window.localStorage["access_token"]+"'></las2peer-file-upload-widget>").appendTo('#fu');
}

function uploadSuccess(){
	var dialog = document.getElementById("fileUpload");
	dialog.close();
	uploadToast.show({text: 'Model successfully uploaded.', duration: 2500});
}

var PS = (function(PS){
	
	
	/**
	* The Provision Service Library
	* @return {Object}
	*/
	PS.ProvisionService = function(){
	
		//Private Properties
		var LAS2PEERHOST = "http://localhost:8080/";
		var LAS2PEERSERVICENAME = "i5.las2peer.services.mobsos.successModeling.MonitoringDataProvisionService";
		var LAS2PEERUSER = "PROVISION_SERVICE_FRONTEND";
		var LAS2PEERUSERPASS = "PSFP";
		
		var LAS2peerClient;
		var loginCallback = function(){};
		
		
		//Private Methods
		/**
		* Retrieves all available nodes.
		* 
		* @param callback Callback function, called when the result has been retrieved. An array of node names.
		*/
		var getCatalogs = function(callback){
			if(LAS2peerClient.getStatus() == "loggedIn"){
				LAS2peerClient.invoke(LAS2PEERSERVICENAME, "getMeasureCatalogs", [], function(status, result) {
					if(status == 200 || status == 204) {
						callback(result.value);
					} else {
						callback("Error! Message: " + result);
					}
				});
			}
		};

		/**
		* Retrieves all available nodes.
		* 
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
		* 
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
		* Retrieves all success models for the given service.
		* 
		* @param serviceName the service name
		* @param update if true, the models are updated via the XML files (service side) before returning results
		* 
		* @param callback Callback function, called when the result has been retrieved. An array of success models.
		*/
		var getSuccessModels = function(serviceName, update, catalog, callback){
			if(LAS2peerClient.getStatus() == "loggedIn"){
			
				var params = [],
				paramServiceName = {},
				paramUpdate = {},
				paramCatalog = {};
				
				paramServiceName.type = "String";
				paramServiceName.value = serviceName;
				paramUpdate.type = "boolean";
				paramUpdate.value = update;
				paramCatalog.type = "String";
				paramCatalog.value = catalog;
				
				params.push(paramServiceName,paramUpdate, paramCatalog);
				
				LAS2peerClient.invoke(LAS2PEERSERVICENAME, "getModels", params, function(status, result) {
					if(status == 200 || status == 204) {
						callback(result.value);
					} else {
						callback("Error! Message: " + result);
					}
				});
			}
		};
		
		/**
		* Visualizes a service success model.
		* 
		* @param modelName the name of the success model
		* @param updateMeasures if true, all measures are updated from XML file
	 	* @param updateModel if true, all models are updated from XML file
	 	* 
		* @param callback Callback function, called when the result has been retrieved. A String.
		*/
		var visualizeServiceSuccessModel = function(modelName, updateMeasure, updateModel, catalog, callback){
			if(LAS2peerClient.getStatus() == "loggedIn"){
				
				var params = [],
				paramModelName = {},
				paramMeasureUpdate = {},
				paramModelUpdate = {};
				paramCatalogName = {};
				
				paramModelName.type = "String";
				paramModelName.value = modelName;
				paramMeasureUpdate.type = "boolean";
				paramMeasureUpdate.value = updateMeasure;
				paramModelUpdate.type = "boolean";
				paramModelUpdate.value = updateModel;
				paramCatalogName.type = "String";
				paramCatalogName.value = catalog;
				
				params.push(paramModelName, paramMeasureUpdate, paramModelUpdate, paramCatalogName);
				
				LAS2peerClient.invoke(LAS2PEERSERVICENAME, "visualizeServiceSuccessModel", params, function(status, result) {
					if(status == 200 || status == 204) {
						callback(result.value);
					} else {
						callback("Error! Message: " + result);
					}
				});
			}
		};
		
		/**
		* Visualizes a node success model.
		* 
		* @param nodeName the name of the node
		* @param updateMeasures if true, all measures are updated from XML file
	 	* @param updateModel if true, all models are updated from XML file
		* 
		* @param callback Callback function, called when the result has been retrieved. A String.
		*/
		var visualizeNodeSuccessModel = function(nodeName, updateMeasure, updateModel,  catalog, callback){
			if(LAS2peerClient.getStatus() == "loggedIn"){
				
				var params = [],
				paramNodeName = {},
				paramMeasureUpdate = {},
				paramModelUpdate = {};
				paramCatalogName = {};
				
				paramNodeName.type = "String";
				paramNodeName.value = nodeName;
				paramMeasureUpdate.type = "boolean";
				paramMeasureUpdate.value = updateMeasure;
				paramModelUpdate.type = "boolean";
				paramModelUpdate.value = updateModel;
				paramCatalogName.type = "String";
				paramCatalogName.value = catalog;
				
				params.push(paramNodeName, paramMeasureUpdate, paramModelUpdate, paramCatalogName);
				
				LAS2peerClient.invoke(LAS2PEERSERVICENAME, "visualizeNodeSuccessModel", params, function(status, result) {
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
			* 
			* @param callback Callback, called when user has been logged in successfully.
			*/
			login: function(callback){
				if(typeof callback == "function"){
					loginCallback = callback;
				}
				if(LAS2peerClient.getStatus() == "loggedIn"){
					loginCallback();
				} else {
					LAS2peerClient.login(LAS2PEERUSER, LAS2PEERUSERPASS, LAS2PEERHOST, "ProvisionServiceFrontend");
				}
			},

			/**
			* Retrieves all available nodes.
			* 
			* @param callback Callback function, called when the result has been retrieved. An array of node names.
			*/
			getCatalogs: function(callback){
				getCatalogs(callback);
			},
			
			/**
			* Retrieves all available nodes.
			* 
			* @param callback Callback function, called when the result has been retrieved. An array of node names.
			*/
			getNodes: function(callback){
				getNodes(callback);
			},
			
			/**
			* Retrieves all available services.
			* 
			* @param callback Callback function, called when the result has been retrieved. An array of service names.
			*/
			getServices: function(callback){
				getServices(callback);
			},
			
			/**
			* Retrieves all available success models for a given service name.
			* @param serviceName the name of the service
			* 
			* @param callback Callback function, called when the result has been retrieved. An array of success model names.
			*/
			getSuccessModels: function(serviceName, catalogName, callback){
				getSuccessModels(serviceName, true, catalogName, callback);
			},
			
			/**
			* Visualizes the "Node Success Model" for a given node.
			* @param nodeName the node name
			* @param callback Callback function, called when the result has been retrieved. A String.
			*/
			visualizeNodeSuccessModel: function(nodeName, catalog, callback){
				nodeName = nodeName.substring(0,12); //Only the id is passed
				visualizeNodeSuccessModel(nodeName, true, true, catalog, callback);
			},
			
			/**
			* Visualizes the success model for a given model name.
			* @param modelName the name of the success model
			* 
			* @param callback Callback function, called when the result has been retrieved. A String.
			*/
			visualizeServiceSuccessModel: function(modelName, catalog, callback){
				visualizeServiceSuccessModel(modelName, true, true, catalog, callback);
			}
		}
		
	};
	
	return PS;
	
})(PS || {});