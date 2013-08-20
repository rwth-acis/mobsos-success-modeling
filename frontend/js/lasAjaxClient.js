/**
 * 
 */


// IE8 does not support console.log... dropping log message in IE then...
if (!window.console) {
	console = {
			log: function() {}
	};
}

/**
 * @class LAS Client which uses Ajax-technologies to communicate with the LAS server
 * If a (valid) session id by another instance is available it is used automatically. If this is not the case,
 * login() must be called to connect to the LAS server.
 * 
 * @param {string} groupName			Optional, if set then it uses the sessionId of members of the same group only (and
 * 							removes the sessionId/connection of the same group only in case of a logout). This 
 * 							is important when more than one instance/groups of instances are running.
 * @param {function} feedbackHandler 	Optional, if set then the provided feedbackHandler is called whenever the login/
 * 							connection/invokation status of the client changes or if an error occurs. Take a 
 * 							look on the included default implementation for more details.
 * @param {string} defaultUser	optional, if set then this login information is used to login automatically 
 * @param {string} defaultPassword optional, if set then this login information is used to login automatically
 * @param {string} defaultServerURL optional, if set then this login information is used to login automatically
 * @param {string} defaultAppCode optional, if set then this login information is used to login automatically
 */
function LasAjaxClient(groupName, feedbackHandler, defaultUsername, defaultPassword, defaultServerURL, defaultAppCode) {
	var that = this;
	
	// private variables
	var sessionId = null;
	var user = null;
	var server = null;
	var appCode = null;
	var status = "unknown";
	var timeout = 3600000;
	var feedbackLog = [];
	var feedback = null;
	var group = "default";
	var jsonStorage = null;
	var lastPingTime = -1;
	var watchdogTimer = null;
	var isLoggingIn = false;
	//TODO: use this
	var lastLoginTimestamp = null;
	
	var defaults = {
			username: null,
			password: null,
			serverURL: null,
			appCode: null
	};
	
	// store default login data if available
	if(typeof(defaultUsername) != "undefined" && defaultUsername != null) {
		defaults.username = defaultUsername;
	}
	if(typeof(defaultPassword) != "undefined" && defaultPassword != null) {
		defaults.password = defaultPassword;
	}
	if(typeof(defaultServerURL) != "undefined" && defaultServerURL != null) {
		defaults.serverURL = defaultServerURL;
	}
	if(typeof(defaultAppCode) != "undefined" && defaultAppCode != null) {
		defaults.appCode = defaultAppCode;
	}
	if(defaults.appCode != null && defaults.password!=null && defaults.serverURL!=null && defaults.username!=null) {
		console.log("Default login credentials are set.");
	}
	
	
	/******************************************/
	/********* PRIVILEGED METHODS *************/
	/******************************************/
	
	/**
	 * Background "thread" which constantly checks the login/connection status. If this instance is not 
	 * connected yet but a session id is available, then it uses this session id automatically. If the connection/login 
	 * breaks down/logout has been performed by another instance, then it updates this instances 
	 * (connection/login) status. Whenever a status change has been connected, the feedbackHandler is
	 * called.
	 */
	this.loginStatusWatchdog = function() {
		
		try {
			if(watchdogTimer != null)
				clearTimeout(watchdogTimer);
			
			// default interval 
			var watchdogInterval = 120000;
			
			if(status != "loggedIn" && defaults.appCode != null && defaults.password != null && defaults.username != null && defaults.serverURL != null) {
				// use default login data
				that.login(defaults.username, defaults.password, defaults.serverURL, defaults.appCode);
				// verify the login in 2 seconds
				watchdogInterval = 2000;
			}
			else if(status != "loggedIn") {
				// see, if session data is available...
				jsonStorage.load(function(retrievedStorage) {
					var storedSessionId = jsonStorage.get("sessionId");
					var storedUser = jsonStorage.get("user");
					var storedServer = jsonStorage.get("server");
					var storedAppCode = jsonStorage.get("appCode");
					
					// verify that it is the same group
					
					
					if(storedSessionId != null && storedUser != null && storedServer != null && storedAppCode != null) {						
						// verify the session via ping
						var pingHandler = function(pingStatus) {
							// for IE 1223 and 200 have to be included as well...
							if(pingStatus == 204 || pingStatus == 1223 || pingStatus == 200) {
								// session is fine
								if(status != "loggedIn" && sessionId != storedSessionId) {
									// login in the meantime?
									status = "loggedIn";
									
									sessionId = storedSessionId;
									user = storedUser;
									server = storedServer;
									appCode = storedAppCode;
									
									feedback(Enums.Feedback.LoginSuccess, "Login (by stored session information) as user " + user + " was successful!");
								}
							}
							else {
								// some kind of error.. just ignore it...no valid data available
								// delete stored session information, if possible...
								jsonStorage.clear();
								jsonStorage.persist(function(response) {
									// ignore results...
								});
							}
						}; 
						that.ping(pingHandler, storedServer, storedSessionId);
					}
					else {
						// check every 5 seconds for session information
						watchdogInterval = 5000;
					}
				});	
			}
			else {
				// should be logged in, first check that the stored sessioninformation have not been deleted in the meantime (logout
				// by another instance), then verify the session if the last ping has been performed more than 1,5 minutes ago
				jsonStorage.load(function(retrievedStorage) {
					var storedSessionId = jsonStorage.get("sessionId");
				
					if(typeof(storedSessionId) == "undefined" || storedSessionId == null) {
						// appearantly, a logout was performed in the meantime...
						status = "loggedOut";
						feedback(Enums.Feedback.LogoutSuccess, "The Session has been closed by another instance.");
					}
					
					var currentTime = (new Date()).getDate();
					if(currentTime - lastPingTime > 90000) {
						// perform a ping/touch of the session
						var pingHandler = function(pingStatus) {
							if(pingStatus == 204 || pingStatus == 1223 || pingStatus == 200) {
								// everything is fine...
							}
							else {
								// some kind of error...
								status = "error";
								// saying a logout was successful anyways in order to make the feedback handler aware of the session status change
								// furthermore, it is quite likely another instance actually logged out...
								feedback(Enums.Feedback.LogoutSuccess, "The Session is not valid anymore. Ping status: " + pingStatus);
								// rerun the watchdog within a second... (to try to use available session data)
								if(watchdogTimer != null)
									clearTimeout(watchdogTimer);
								watchdogTimer = setTimeout(function() {
									that.loginStatusWatchdog();
								},1000);
							}
						}; 
						that.ping(pingHandler, null, null);
					}
				});
			}
			
			// run the watchdog later on again...
			watchdogTimer = setTimeout(function() {
					that.loginStatusWatchdog();
				}, watchdogInterval);
		}
		catch(error) {
			feedback(Enums.Feedback.Error, "The login/session watchdog died - the login/session status is no longer monitored! Message: " + error);
		}
	};
	
	
	/**
	 * Logs in (creates a session) for the user and all instances (of the same group only, if the
	 * groupName has been set)
	 * 
	 * @param username
	 * @param password
	 * @param serverURL
	 * @param applicationCode
	 */
	this.login = function(username, password, serverURL, applicationCode) {
		try {
			if(isLoggingIn) {
			    feedback(Enums.Feedback.Warning, "Login is already in progress. Please wait!");
				return;
			}
		  
			isLoggingIn = true;
		  
			// check the status of the connection
			if(status == "loggedIn") {
				feedback(Enums.Feedback.Warning, "Already logged in, aborting login. Please logout first!");
				return;
			}
			
			
			
			
			// check the paramaters...
			if(typeof(username) == "undefined" || username == null) {
				throw "Must provide a username for login!";
			}
			if(typeof(password) == "undefined" || password == null) {
				throw "Must provide a password for login!";
			}
			if(typeof(serverURL) == "undefined" || serverURL == null) {
				throw "Must provide a server url for login!";
			}
			if(typeof(applicationCode) == "undefined" || applicationCode == null) {
				throw "Must provide an  application code for login!";
			}
			
			// store them, if they seem ok...
			user = username;
			server = serverURL;
			appCode = applicationCode;
			
			// create some handlers...
			var onLoadHandler = function(xhr) {
				// login was successful
				status = "loggedIn";
				isLoggingIn = false;
				
				var xml = xhr.responseXML;
				try {
					sessionId = xml.getElementsByTagName('id')[0].firstChild.data;
				}
				catch(error) {}
				
				if(typeof(sessionId) == "undefined") {
					try {
						sessionId = xml.documentElement.childNodes[0].text;
					}
					catch(error) {}
				}
				
				if(typeof(sessionId) == "undefined" || sessionId == null) {
					status = "error";
					feedback(Enums.Feedback.LoginError, "Login failed. Status code: " + xhr.status);
					return;
				}
				
				feedback(Enums.Feedback.LoginSuccess, "Login as user " + user + " was successful!");

				// share the session information with other instances of the same group
				// update the storage
				jsonStorage.load(function(retrievedStorage) {});
				jsonStorage.set("sessionId", sessionId);
				jsonStorage.set("user", user);
				jsonStorage.set("server", server);
				jsonStorage.set("appCode", appCode);
				jsonStorage.persist(function(response) {
					if(response != true) {
						feedback(Enums.Feedback.Warning, "Login was successful but sharing the session information with other group instances failed!");
					}
				});
			};
			
			var onErrorHandler = function(xhr) {
				isLoggingIn = false;
				status = "loggedOut";
				feedback(Enums.Feedback.LoginError, "Login failed. Status code: " + xhr.status);
			};
			
			// send the request...
			var xhr = getXMLHttpRequest("get", server + 'createsession?user=' + user + '&passwd=' + password + '&timeout=' + timeout, onLoadHandler, onErrorHandler, "appcode", appCode);
			xhr.send();
			
		}
		catch(error) {
			feedback(Enums.Feedback.Error, "Login failed and caused an exception: " + error);
		}
	};
	
	/**
	 * Logs out/closes the session for the current user and all instances (of the same group only, 
	 * if the groupName has been set)
	 */
	this.logout = function() {
		try {
			if(status != "loggedIn") {
				// delete stored session information, if available
				jsonStorage.clear();
				jsonStorage.persist(function(response) {
					// ignore results...
				});
				feedback(Enums.Feedback.Warning, "Already logged out, therefore aborting logout().");
				return; 
			}
			
			var onLoadHandler = function(xhr) {
				sessionId = null;
				status = "loggedOut";
				
				// delete stored session information
				jsonStorage.clear();
				jsonStorage.persist(function(response) {
					if(response != true) {
						feedback(Enums.Feedback.Warning, "Clearing/deleting stored session information failed!");
					}
				});
				
				feedback(Enums.Feedback.LogoutSuccess, "Logged out successfully.");
			};
			var onErrorHandler = function(xhr) {
				switch(xhr.status) {
					case 401:
					case 403:
					case 412:
						status = "loggedOut";
						feedback(Enums.Feedback.LogoutSuccess, "Logged out successfully (Session was already closed).");
						break;
					default:
						feedback(Enums.Feedback.LogoutError, "Logout was not successful, cause unknown. Assuming that the session is not valid/connection is broken.");
						status = "error";
						break;
				}
				// delete stored session information
				jsonStorage.clear();
				jsonStorage.persist(function(response) {
					if(response != true) {
						feedback(Enums.Feedback.Warning, "Clearing/deleting stored session information failed!");
					}
				});
				
				sessionId = null;
			};
			
			var xhr = getXMLHttpRequest("GET", server + 'closesession?SESSION=' + sessionId, onLoadHandler, onErrorHandler, "appcode", appCode);
			xhr.send();
		}
		catch(error) {
			feedback(Enums.Feedback.Error, "Logout failed and caused an exception: " + error);
		}
	};
	
	/**
	 * Invokes a method of a LAS service. 
	 * 
	 * @param parametersAsJSONArray Parameters for the methods. Have to be encoded as JSON object, in particular as an array. E.g.:
	 * 								parametersAsJSONArray = new Array();
	 * 								parametersAsJSONArray[0] = {"type": "string", "value": "Hello World!"};
	 * 								...
	 * @param invokationHandler		Function which is called upon completion/success/error of the invocation. It must be of the form
	 * 								function(status, result)
	 * 								where status == 200 on success and result is a JSON-Object with the invoked methods' response/result. This result is 
	 * 								encoded as an array exactly like the parameters.  If the status is 0 then the session is broken and one has to login again
	 */
	this.invoke = function(serviceName, methodName, parametersAsJSONArray, invokationHandler) {
		try {
			// checking the parameters...
			if(typeof(serviceName) == "undefined" || serviceName == null || serviceName.length < 1) {
				throw "Must provide the name of the service to be invoked!";
			}
			if(typeof(methodName) == "undefined" || methodName == null || methodName.length < 1) {
				throw "Must provide the name of the method to be invoked!";
			}
			if(typeof(parametersAsJSONArray) == "undefined" || parametersAsJSONArray == null) {
				throw "Must provide the parameters as JSON Array, if you don't want to pass on parameters provide an empty JSON array!";
			}
			if(typeof(invokationHandler) != "function") {
				invokationHandler = function(status, result) {
					// do nothing...
				};
				feedback("warning", "No invokation handler was provided for the invokation of " + serviceName + "." + methodName + ". The response will we ignored/dropped but information wether the invokation was successful will always be provided with the feedback handler..");
			}
			
			if(status != "loggedIn") {
				feedback(Enums.Feedback.InvocationAbort, "Not logged in, therefore aborting the invokation of " + serviceName + "." + methodName);
				return; 
			}
			
			// the parameters have to be embedded in XML, this is done here
			var parameterXMLString = buildXMLParameterStringFromJSON(parametersAsJSONArray);
			
			var url = server + serviceName + '/' + methodName + '?SESSION=' + sessionId;
			
			var onLoadHandler = function(xhr) {
				try {
					if(xhr.status != 200 && xhr.status != 204) {
						var errorExplaination = that.explainLASResponseCode(xhr.status);
						feedback(Enums.Feedback.InvocationError, "The invocation of " + serviceName + "." + methodName + " failed! Error code: " + xhr.status + " " + errorExplaination);
						invokationHandler(xhr.status, errorExplaination);
						return;
					}
					// extract the XML-encoded response and return it in a JSON object
					var result = {};
					var returnType = null;
					//sessionId = xml.getElementsByTagName('id')[0].firstChild.data;
					//sessionId = xml.documentElement.childNodes[0].text;
					
					if(xhr.status != 204) {
						if (xhr.responseXML.getElementsByTagName("param")[0].getAttribute("type") == "Array") {
							// got an array...
							returnType = xhr.responseXML.getElementsByTagName("param")[0].getAttribute("class");
							var values = new Array();
							for (var i = 0; i < xhr.responseXML.getElementsByTagName("element").length; i++) {
								values[i] = xhr.responseXML.getElementsByTagName("element")[i].firstChild.data;
							}
							result = {"type": returnType + "[]" , "value": values};
						}
						else {
							// just one value (but which could be a string with a JSON-Object, HTML-Table, XML, ...)
							returnType = xhr.responseXML.getElementsByTagName("param")[0].getAttribute("type");
//							result = {"type": returnType , "value": xhr.responseXML.getElementsByTagName('param')[0].firstChild.data};
							result = {"type": returnType , "value": xhr.responseXML.getElementsByTagName('param')[0].textContent};
						}
					}
					else {
						result = {"type": "none" , "value": ""};
					}
					
					feedback(Enums.Feedback.InvocationSuccess, "Invocation of " + serviceName + "." + methodName + " was successful.");
					invokationHandler(xhr.status, result);
					
					// try to reset the session timeout
					that.ping(null, null, null);
				}
				catch(error) {
					feedback(Enums.Feedback.InvocationError, "Handling the invocation result of " + serviceName + "." + methodName + " failed and caused an exception: " + error);
				}
			};
			
			var onErrorHandler = function(xhr) {
				var errorExplaination = that.explainLASResponseCode(xhr.status);
				feedback(Enums.Feedback.InvocationError, "The invocation of " + serviceName + "." + methodName + " failed! Error code: " + xhr.status + " " + errorExplaination);
				invokationHandler(xhr.status, errorExplaination);
			};
			
			var xhr = getXMLHttpRequest("POST", url, onLoadHandler, onErrorHandler, "Content-type", "application/xml");
			xhr.send(parameterXMLString);
			
			feedback(Enums.Feedback.InvocationWorking,  methodName + " of the service " + serviceName + " is being invoked...");
		}
		catch(error) {
			feedback(Enums.Feedback.Error, "Invokation of " + serviceName + "." + methodName + " failed and caused an exception: " + error);
		}
	};
	
	/**
	 * Touches/pings the session (resets the session's timeout) as described 
	 * in the LasHttpConnectorProtocol description.
	 * 
	 * @param pingHandler Function, which must be of the form: function(status). If status == 204, then the ping was successful
	 * 					  and the connection/sessionId is valid 
	 * @param customSessionId If another session id, not the currently used one/saved one (in the variable sessionId)
	 */
	this.ping = function(pingHandler, customServer, customSessionId) {		
		try {
			if(typeof(customServer) == "undefined" || customServer == null) {
				customServer = server;
			}
			
			if(typeof(customSessionId) == "undefined" || customSessionId == null) {
				customSessionId = sessionId;
			}
			
			if(typeof(pingHandler) == "undefined" || pingHandler == null) {
				feedback("warning", "Did not provided a callback handler for the session ping! Using a default handler.");
				pingHandler = function(status) {
					if(customSessionId == sessionId) {
						lastPingTime = (new Date()).getDate();
					}
					
					// Win error 1223 => HTTP Status 1223
					if(status == 204 || status == 1223) {
						feedback(Enums.Feedback.PingSuccess, "Ping/Touch of the session was successfully.");
					}
					else {
						feedback(Enums.Feedback.PingError, "Ping/Touch of the session failed! Error code: " + status + " " + that.explainLASResponseCode(status));
					}
				}; 
			}
			
			
			var onLoadHandler = onErrorHandler = function(xhr) {
				pingHandler(xhr.status);
			};
			
			var xhr = getXMLHttpRequest("GET", customServer + 'touchsession?SESSION=' + customSessionId, onLoadHandler, onErrorHandler, "appcode", appCode);
			xhr.send();
		}
		catch(error) {
			feedback(Enums.Feedback.Error, "Exception while pinging/touching the session: " + error);
		}
	};
	
	/**
	 * Allows to set custom session data, if sessionId is known but username/password is not.
	 * If it worked, the feedbackhandler will be notified
	 */
	this.setCustomSessionData = function(customSessionId, customUsername, customServerURL, customApplicationCode) {
		try {
			// check the paramaters...
			if(typeof(customUsername) == "undefined" || customUsername == null) {
				throw "Must provide a username when setting custom session data!!";
			}
			if(typeof(customSessionId) == "undefined" || customSessionId == null) {
				throw "Must provide a sessionId when setting custom session data!";
			}
			if(typeof(customServerURL) == "undefined" || customServerURL == null) {
				throw "Must provide a server url when setting custom session data!!";
			}
			if(typeof(customApplicationCode) == "undefined" || customApplicationCode == null) {
				throw "Must provide a applicationCode when setting custom session data!!";
			}
			
			// update the storage so the guard dog will find it...
			jsonStorage.load(function(retrievedStorage) {});
			jsonStorage.set("sessionId", customSessionId);
			jsonStorage.set("user", customUsername);
			jsonStorage.set("server", customServerURL);
			jsonStorage.set("appCode", customApplicationCode);
			jsonStorage.persist(function(response) {
				if(response != true) {
					feedback(Enums.Feedback.Warning, "Setting the custom session data failed! Can set the storage object!");
				}
				// let the background guard dog find the session data and test it
				that.verifyStatus();
			});
		}
		catch(error) {
			feedback(Enums.Feedback.Error, "Exception when trying to set custom session data: " + error);
		}
	};
	
	/**
	 * Forces this instance to check for available session data
	 * (Logins by other instances)
	 * 
	 */
	this.verifyStatus = function() {
		that.loginStatusWatchdog();
	};
	
	this.getUsername = function() {
		return user;
	};
	
	this.getSessionId = function() {
		return sessionId;
	};
	this.getServer = function() {
		return server;
	};
	
	this.getAppCode = function() {
		return appCode;
	};
	
	this.getStatus = function() {
		return status;
	};
	
	this.getTimeout = function() {
		return timeout;
	};
	
	this.getFeedback = function() {
		return feedbackLog[feedbackLog.length];
	};
	
	this.getFeedbackLog = function() {
		return feedbackLog;
	};
	
	this.getGroup = function() {
		return group;
	};
	
	/******************************************/
	/********* PRIVATE METHODS ****************/
	/******************************************/
	
	/**
	 * If no feedbackHandler has been provided during instanciation, then this default implementation
	 * is used. Status changes and errors are written to console.log. 
	 * @param statusCode String with the new status/error code
	 * @param message String, which describes the new status and/or error
	 */ 
	function defaultFeedbackHandler(statusCode, message) {
		//TODO
	};
	
	/**
	 * Returns a XMLHTTPRequest object, which is constructed according to the features of the used browser.
	 * In particular, the IE requires special care...
	 * 
	 * @param type Either GET or POST
	 * @param url URL for the request...
	 * @param onLoadHandler Function of the form function(xhr), which is called upon success
	 * @param onErrorHandler Function of the form function(xhr), which is called upon an error
	 * @param requestHeaderName Optional, name of a custom header
	 * @param requestHeaderValue Optional, value of a custom header
	 */
	function getXMLHttpRequest(type, url, onLoadHandler, onErrorHandler, requestHeaderName, requestHeaderValue) {
		try {
			
			if(typeof(onLoadHandler) == "undefined" || typeof(onErrorHandler) == "undefined") {
				feedback(Enums.Feedback.Warning, "No handlers (onLoad/onError) for a XMLHttp request have been set. Setting default ones.");
				
				onLoadHandler = function(xhr) {
					feedback(Enums.Feedback.Success, "XMLHttpRequest terminated successfully.");
				};
				onErrorHandler = function(xhr) {
					feedback(Enums.Feedback.Error, "XMLHttpRequest resulted in an error. Status/Error code: " + xhr.status);
				};
			}
			
			if (XMLHttpRequest) {
				var xhr = new XMLHttpRequest();
				
				if ("withCredentials" in xhr) {
					// for Firefox, Safari, Chrome
					// Primary resource: https://developer.mozilla.org/en/using_xmlhttprequest
					
					// set the handlers (have to be set before open())
					xhr.addEventListener("load", function(evt) {
							onLoadHandler(xhr);
						}, false);
					xhr.addEventListener("error", function(evt) { 
							onErrorHandler(xhr);
					}, false);
		
					xhr.open(type, url, true);
					
					if(requestHeaderName != null && requestHeaderValue != null) {
						xhr.setRequestHeader(requestHeaderName, requestHeaderValue);
					}					
				}
				else if(window.XDomainRequest) {
					// IE (8+)
					// Primary resource: http://msdn.microsoft.com/en-us/library/cc288060%28v=vs.85%29.aspx
					// Useful, too: http://ajaxian.com/by/topic/xmlhttprequest
					try {
						xhr = new  ActiveXObject("MSXML2.XMLHTTP.3.0");
						
						xhr.onreadystatechange = function() { 
							if(xhr.readyState == 4)	{
								if(xhr.status == 200 || xhr.status == 204 || xhr.status == 1223) {
									onLoadHandler(xhr); 
								}
								else {
									onErrorHandler(xhr);
								}
							}
						};
						
						xhr.open(type, url);
						
						if(requestHeaderName != null && requestHeaderValue != null) {
							xhr.setRequestHeader(requestHeaderName, requestHeaderValue);
						}
					}
					catch(errorMSXML) {
						if(type == "post" || type == "POST") {
							alert("Failed to use IE MSXML2.XMLHTTP.3.0. \nProbably your IE is not configured to allow \"Access data sources across domains\" and ActiveX\nFalling back to XDomainRequest but due to a bug in LAS invocations can not be performed!");
						}
					
						feedback(Enums.Feedback.Warning, "Failed to instanciate Msxml2.XMLHTTP (does your IE config allow for ActiveX and \"Access data sources across domains\"?). Trying XDomainRequest now but invocations will fail because the content-type can not be set! Message: " + errorMSXML);
						xhr = new XDomainRequest();
						
						/*
						 * MSDN
						 * "we restricted the content type to text/plain"
						 * "To workaround this issue, server code that currently processes HTML Forms must be rewritten to manually parse the request body into name-value pairs when receiving requests from XDomainRequest objects. This makes adding support for the XDomainRequest object more difficult than it would be otherwise."
						 */
						
						// XDomainRequest does not support setting headers on purpose:
						// http://blogs.msdn.com/b/ieinternals/archive/2010/05/13/xdomainrequest-restrictions-limitations-and-workarounds.aspx
						
						
						if(xhr) {
							// XDomainRequest does not have the status field...
							xhr.status = 0;
							
							// set the handlers
							xhr.onload = function() { 
									xhr.status = 200;
									// XDomainRequest does not support responseXML
									// trying to workaround and get/set the XML manually...
									xmlDoc=new ActiveXObject("Microsoft.XMLDOM");
									xmlDoc.async="false";
									xmlDoc.onreadystatechange = function() {
										if(xmlDoc.readyState == 4) {
											xhr.responseXML = xmlDoc;		
											onLoadHandler(xhr); 
										}
									};
									xmlDoc.loadXML(xhr.responseText);
								};
							xhr.timeout = function() {
									onErrorHandler(xhr);
								};
							xhr.onerror = function() {
									onErrorHandler(xhr);
								};
							xhr.onprogess = function() {
								// ignoring that for now...
							};
							
							xhr.timeout = "30000";
							xhr.open(type, url);
						}
						else {
							feedback(Enums.Feedback.Error, "Failed to instanziate a XDomainRequest, the specific cause is unknown.");
						}
					}
				}
				else {
					throw "Problem with CORS/XMLHttpRequest: neither withCredentials nor XDomainRequest are supported by your browser! Supported browsers include: Firefox > 3.5 or Chrome > 8 or Safari > ? or IE > 8";
				}
				
				return xhr;
			}
			else {
				throw "Problem with CORS/XMLHttpRequest: neither withCredentials nor XDomainRequest are supported by your browser! Supported browsers include: Firefox > 3.5 or Chrome > 8 or Safari > ? or IE > 8";
			}
		}
		catch(error) {
			feedback(Enums.Feedback.Error, "Preparing a XML HTTP request failed due to an exception: " + error);
			throw "Exception while preparing a XML request. Message: " + error;
		}
	};
	
	/**
	 * Writes the JSON-encoded invocation parameters into an XML-String the LAS-Server/HTTP-Connector supports
	 * 
	 * @param parametersAsJSONArray
	 */
	function buildXMLParameterStringFromJSON(parametersAsJSONArray) {
		try {
			if(typeof(parametersAsJSONArray) == "undefined" || parametersAsJSONArray == null) {
				throw "You need to provide a JSON Array with the paramaters which are to be encoded. If you do not want to encode any paramaters, provide an empty JSON array!";
			}
			
			var paramCount = parametersAsJSONArray.length;

			// start with the XML header
			var parameterXMLString = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<objectlist count=\""+ paramCount + "\" >\n";
			
			// add the individual parameters
			for(var i = 0; i < paramCount; i++) {
				if (/\[\]$/.test(parametersAsJSONArray[i]["type"])) {
					// it is an array
					var arrayType = parametersAsJSONArray[i]["type"].substring(0, parametersAsJSONArray[i]["type"].length-2);
					if (arrayType == "string") {
						arrayType = "String";
					}
					var arrayLength = parametersAsJSONArray[i]["value"].length;
					
					parameterXMLString += "\t<param type=\"Array\" class=\""+arrayType+"\" length=\"" + arrayLength + "\">\n";
					
					if(arrayType.toLowerCase() == "string") {
						for (var a = 0; a < arrayLength; a++){
							parameterXMLString += "\t\t<element><!\[CDATA\[" + parametersAsJSONArray[i]["value"][a] + "\]\]></element>\n";

						}
					}
					else {
						for (a = 0; a < arrayLength; a++){
							parameterXMLString += "\t\t<element>" + parametersAsJSONArray[i]["value"][a] + "</element>\n";
						}
					}
					parameterXMLString += "\t</param>\n";
				}
				else {
					// it is one value only
					if(parametersAsJSONArray[i]["type"].toLowerCase() == "string") {
						parameterXMLString += "\t<param type=\"String\"><!\[CDATA\[" + parametersAsJSONArray[i]["value"] + "\]\]></param>\n";
					}
					else {
						parameterXMLString += "\t<param type=\""+parametersAsJSONArray[i]["type"]+"\">" + parametersAsJSONArray[i]["value"] + "</param>\n";
					}
				}
			}
			
			// end with the XML footer
			parameterXMLString +=  "</objectlist>";
			// return the complete result
			return parameterXMLString;
		}
		catch(error) {
			feedback(Enums.Feedback.Error, "Exception when building the request XML file for the invocsation: " + error);
			throw "Exception when building XML parameter string. Message: "+error;
		}
	};
	
	/******************************************/
	/********* INITIALIZATION *****************/
	/******************************************/
	
	// set the feedback hander
	if(typeof(feedbackHandler) != "function" || feedbackHandler == null) {
		feedback = defaultFeedbackHandler;
	}
	else {
		feedback = feedbackHandler;
	}
	
	// set the groupname
	if(typeof(groupName) != "undefined" && groupName != null) {
		group = groupName;
	}
	else {
		group = "all";
	}
	
	// set the storage backend, for cookies are being used
	// TODO: test backends one by one?
	jsonStorage = new Storage("lasClient_" + group, "Persistent");
	
	console.log("Configured for group: " + group +","+groupName);
	
	// start up the login/status watchdog
	that.loginStatusWatchdog();
}

/******************************************/
/********* PUBLIC METHODS *****************/
/******************************************/


/**
 * Returns a string with a short description of the meaning of a status/response code 
 * returned by a XMLHTTP-Request to the LAS server/HTTP-Connector.
 * 
 * @param code The status/response code returned by LAS for each request (internally usually xhr.status).
 */
LasAjaxClient.prototype.explainLASResponseCode = function(code) {
	switch(code) {
		case 0:
			return "Connection and/or Session problems. No further informations available. If you use Firefox: try another browser or tools like Firebug to get more information - Firefox always returns 0 instead of the detailed status codes. Otherwise check your internet connection, try to logout and login again.";
		case 401:
			return "Tried to access a non existent session or to invoke without a valid session. Try to logout and login again.";
		case 403:
			return "Tried to access a session from an invalid IP/Host OR access to the service method has been denied.";
		case 404:
			return "Service or method does not exist.";
		case 406:
			return "Invalid parameter encoding";
		case 412:
			return "Session timeout occurred. Try to logout and login again.";
		case 500:
			return "Exception thrown during the invokation.";
		case 501:
			return "The result of the Invokation cannot be transported.";
		default:
			return "The status/response code " + code + " is unknown!";
	}
};


/******************************************/
/**************** ENUMS *******************/
/******************************************/
function Enums() {}
Enums.Feedback = {
	// general ones, should be avoided
	Error: "error",
	Warning: "warning",
	Success: "success",
	
	InvocationError: "invocationError",
	InvocationAbort: "invocationAbort",
	InvocationSuccess: "invocationSuccess",
	InvocationWorking: "invocationWorking",
	PingError: "pingError",
	PingSuccess: "pingSuccess",
	LoginError: "loginError",
	LoginSuccess: "loginSuccess",
	LogoutError: "loginError",
	LogoutSuccess: "logoutSuccess"	
};
