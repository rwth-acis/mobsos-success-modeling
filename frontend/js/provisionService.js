/**
* Provision Service Application Script
* @author Peter de Lange (lange@dbis.rwth-aachen.de)
*/


var psLibrary = new PS.ProvisionService();


/**
* Logs in the anonymous user.
*/
var login = function(){
	psLibrary.login(function(){
		//Do something
	});
};


//Login by default
login();