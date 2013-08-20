/**
 * 
 */

// Please note, that this is a rather high-traffic workaround...
// OpenSocial: does not work correctly... only VIEWER data is implemented for now...
// For OpenSocial check the restrictions here: http://wiki.opensocial.org/index.php?title=The_Persistence_API
// TODO: for OpenSocial don't persist the whole object but the keys only to save bandwidth
// TODO: use a version value


// IE8 does not support console.log... dropping log message in IE then...
if (!window.console) {
	console = {
			log: function() {}
	};
}

/**
 * Storage function, which wraps various storage backends in a transparent manner.
 * A storage can be loaded, it's values get/set and persistet to the used storage backend.
 * Currently, Cookies and HTML5 (LocalStorage) are stable. OpenSocial AppData is not 
 * stable yet,
 * 
 * @param storageName	Identifier of the storage (!) object to be loaded
 * @param storageBackend	Name of the storage backend, e.g. "Cookie" or "HTML5"
 */
function Storage(storageName, storageBackend) {
	var that = this;
	
	var storage = null;
	var backend = null;
	var name = storageName;
	var isLoading = false;
	
	try {
		// check which/a valid storage backend is to be used...
		if(typeof(storageBackend) == "undefined" || storageBackend == null) {
			// fall back to cookies...
			console.log("Warning: did not provided the name of the backend which is to be used. Falling back to auto/persistent!");
			storageBackend = "Persistent";
		}
		
		if(storageBackend == "Persistent") {
			storageBackend = getBestPersistentBackendName();
		}
		
		switch(storageBackend) {
			case "Cookie":
			case "HTML5":
			case "OpenSocial":
				break;
			case "SetPref":
			default:
				throw "The storage backend is not known/unimplemented: " + storageBackend;
		}
		backend = storageBackend;
		
		// try to load it
		try {
			load(function(retrievedStorage) {});
		}
		catch(loadError) {
			// ignore it, probably the storage object has just not been used/persistet before 
		}
	}
	catch(error) {
		throw "Instanciating a JSONStorage failed. Exception: " + error;
	}
	
	
	/*
	 * Privileged Methods
	 */
	
	/**
	 * Determines the "best" persistent backend, e.g. if HTML5 is available it returns HTML5 instead of Cookies
	 * NOT FULLY IMPLEMENTED YET
	 * 
	 * @returns {string} Name of the best backend
	 */
	function getBestPersistentBackendName() {
		try {
			//TODO PrefSet, OpenSocial, ...
			
			if('localStorage' in window && window['localStorage'] !== null) {
				// HTML5 is support, use it
				return "HTML5";
			}
			
			return "Cookie";
		}
		catch(error) {
			throw "getBestPersistentBackendName: " + error;
		}
	}
	
	/**
	 * Loads/retrieves the storage object in which all values are stored.
	 * Must be called before the first get/set operations.
	 * 
	 * @param callback Optional, of the form function(retrievedStorage).
	 */
	that.load = function(callback) {
		try {
			var storageObject = null;
			
			if(typeof(callback) == "undefined" || callback == null) {
				callback = function(retrievedStorage) {};
			}
			
			switch(backend) {
				case "Cookie":
					if (document.cookie.length>0) {
						var cookieStart=document.cookie.indexOf(name+"=");
						var cookieEnd = -1;
						if (cookieStart != -1) {
							cookieStart=cookieStart + name.length + 1;
							cookieEnd=document.cookie.indexOf(";",cookieStart);
							if(cookieEnd==-1) {
								cookieEnd=document.cookie.length;
							}
							storageObject = document.cookie.substring(cookieStart,cookieEnd);
							// stupid replacement...get better onces!
							storageObject = storageObject.replace(/#--#/g,"#");
							storageObject = storageObject.replace(/##/g,";");
						 }
					}
					break;
				case "HTML5":
					storageObject = localStorage.getItem(name); 
					break;
				case "OpenSocial":
					var opensocialRequest = opensocial.newDataRequest(); 
					var idspec = opensocial.newIdSpec({ "userId" : "VIEWER", "groupId" : "SELF" }); 
					opensocialRequest.add(opensocialRequest.newFetchPersonAppDataRequest(idspec, name),"get_data");
					opensocialRequest.send(function(response) {
						if (response.get("get_data").hadError()) {
							callback(null);
						} 
						else {
							storageObject = response.get("get_data").getData();
							if(typeof(storageObject) == "undefined" || storageObject == null) {
								storageObject = {};
							}
							else {
								storageObject = JSON.parse(unescape(storageObject));
							}
							storage = storageObject;	
							callback(storage);
						}
					});
					break;
				case "SetPref":
				default:
					callback(null);
					throw "The storage backend is not known/unimplemented: " + storageBackend;
			}
			
			if(typeof(storageObject) == "undefined" || storageObject == null) {
				storageObject = {};
			}
			else {
				storageObject = JSON.parse(unescape(storageObject));
			}
			storage = storageObject;	
			
			callback(storage);
		}
		catch(error) {
			throw "Getting the storage object caused an exception: " + error;
		}
	};
	
	
	/**
	 * Persists the storage object to the backend.
	 * 
	 * @param callback Optional, of the form function(response), response == true if successful, false otherwise
	 */
	that.persist = function(callback) {
		try {
			if(typeof(callback) == null) {
				callback = function(response) {};
			}
			
			// see if we have any settings yet otherwise try to load them or initialize them...
			if(typeof(storage) == "undefined" || storage == null) {
				try {
					// load it...
					if(!isLoading) {
						// to avoid a build up retrieve requests...
						// running conditions can still occur, but it is sufficiently unlikely for this purpose...
						isLoading = true;
						
						load(function(retrievedStorage) {
							isLoading = false;
							
							if(retrievedStorage == null) {
								// initialize it..
								storage = {};
							}
							else {
								// should not be necessary, already done by load()
								storage = retrievedStorage;
							}
							persist(callback);
						});
					}
					// abort the execution of this function call,
					// wait for the load-callback
					return;
				}
				catch(error) {
					throw "Exception when trying to load the storage object before persisting it. Error: " + error;
				}
			}
			
			// set the object according to the used storage backend
			var storageValue = escape(JSON.stringify(storage));
			
			switch(backend) {
				case "Cookie":
					var expireDate=new Date();
					expireDate.setDate(expireDate.getDate()+9000);
					
					// stupid replace - should be replace when possible...
					storageValue = storageValue.replace(/#/g,"#--#");
					storageValue = storageValue.replace(/;/g, "##");
					
					document.cookie=name + "=" + storageValue + ";expires=" + expireDate.toUTCString();					
					break;
				case "HTML5":
					localStorage.setItem(name, storageValue); 
					break;
				case "OpenSocial":
					var opensocialRequest = opensocial.newDataRequest(); 
					opensocialRequest.add(opensocialRequest.newUpdatePersonAppDataRequest("VIEWER", name, storageValue), "set_data");
					opensocialRequest.send(function(response) {
						if (response.get("set_data").hadError()) {
							console.log("Saving OpenSocial Appdata failed: " + JSON.stringify(reponse));
							callback(false);
						}
						else {
							callback(true);
						}
					});
					break;
				case "SetPref":
				default:
					callback(false);
					throw "The storage backend is not known/unimplemented: " + storageBackend;
			}
			
			callback(true);
		}
		catch(error) {
			/*
			if(error == QUOTA_EXCEEDED_ERR) {
				callback(false);
				throw "Setting the storage object with HTML5 local storage failed because you exceeded your quota. Message: " + error;
			}
			else {
				callback(false);
				throw "Setting the storage object caused an exception: " + error;
			}*/
			throw "Setting the storage object caused an exception: " + error;
		}
	};
	
	/**
	 * Returns a specific value from the storage object. Load should be called
	 * before to make sure the storage object is up-to-date.
	 * 
	 * @param key The key of the value
	 * @returns The value. If the key/value does not exist: null
	 */
	that.get = function(key) {
		var that = this;
		
		try {
			var value = storage[key];
				
			if(typeof(value) == "undefined" || value == null) {
				value = null;
			}
			
			return value;
		}
		catch(error) {
			throw "Getting the value with the key " + key + " caused an error: " + error;
		}
	};

	/**
	 * Sets/adds a specific value with a key. Be aware that the key/value won't
	 * be persisted automatically - you need to call persist()
	 * 
	 * @param key
	 * @param value
	 */
	that.set = function(key, value) {
		var that = this;
		
		try {
			storage[key] = value;
		}
		catch(error) {
			throw "Setting the value of the key " + key + " caused an error: " + error;
		}
	};

	/**
	 * Deletes/clears the storage object (completely!). Be aware that persist()
	 * has to be called to delete all values in the backend too/persist the clearance.
	 */
	that.clear = function() {
		var that = this;
		
		try {
			storage = {};
		}
		catch(error) {
			throw "Clearing/deleting all values in the storage failed/caused an exception. Error: " + error;
		}
	};
}