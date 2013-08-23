/**
* Provision Service Application Script
* @author Peter de Lange (lange@dbis.rwth-aachen.de)
*/


var psLibrary = new PS.ProvisionService();


	//Left
	nodeNameNode = document.getElementById("ps_nodeName"),
	nodeSelectNode = document.ps_visualizeNodeModel.NodeSelection,
	
	nodeSuccessModelNode = document.getElementById("ps_nodeSuccessModel"),
	
	//Right
	serviceNameNode = document.getElementById("ps_serviceName"),
	serviceSelectNode = document.ps_visualizeServiceModel.ServiceSelection,
	
	successModelNameNode = document.getElementById("ps_successModelName"),
	successModelSelectNode = document.ps_visualizeServiceModel.SuccessModelName,
	
	serviceSuccessModelNode = document.getElementById("ps_serviceSuccessModel"),

	
	//Message Input
	messageNode = document.getElementById("cs_message");

/**
* Logs in the anonymous user.
*/
var login = function(){
	psLibrary.login(function(){
		get_nodes();
		get_services();
	});
};

/**
 * Returns a list of Nodes and writes them into the corresponding select form.
 */
var get_nodes = function(){
	psLibrary.getNodes(function(result){
		if(result != null){
			nodeSelectNode.options.length=0
			for (var i = 0; i < result.length; i++) {
				nodeSelectNode[i]=new Option(result[i]);
			}
		}
	});
};

/**
 * Returns a list of services and writes them into the corresponding select form.
 */
var get_services = function(){
	psLibrary.getServices(function(result){
		if(result != null){
			serviceSelectNode.options.length=0
			for (var i = 0; i < result.length; i++) {
				serviceSelectNode.options[i]=new Option(result[i]);
			}
		}
	});
};

/**
 * Returns a list of service success models corresponding to the currently selected service.
 * Writes them to the corresponding select form.
 */
var get_success_models = function(){
	var serviceName = serviceSelectNode.options[serviceSelectNode.selectedIndex].text;
	psLibrary.getSuccessModels(serviceName, function(result){
		if(result != null){
			successModelSelectNode.options.length=0
			for (var i = 0; i < result.length; i++) {
				successModelSelectNode.options[i]=new Option(result[i]);
			}
		}
	});

};

//Login by default
login();
