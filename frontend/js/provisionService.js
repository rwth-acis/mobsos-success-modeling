/**
* Provision Service Application Script
* @author Peter de Lange (lange@dbis.rwth-aachen.de)
*/


var psLibrary = new PS.ProvisionService();


	//Left
	nodeSelectNode = document.ps_visualizeNodeModel.NodeSelection,
	nodeSuccessModelNode = document.getElementById("ps_nodeSuccessModel"),
	
	//Right
	serviceSelectNode = document.ps_visualizeServiceModel.ServiceSelection,
	successModelSelectNode = document.ps_visualizeServiceModel.SuccessModelName,
	serviceSuccessModelNode = document.getElementById("ps_serviceSuccessModel");
	
	
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
	
/**
 * Visualizes a node success model and writes it to the output div.
 */
var visualize_node_success_model = function(){
	var nodeName = nodeSelectNode.options[nodeSelectNode.selectedIndex].text;
	psLibrary.visualizeNodeSuccessModel(nodeName, function(result){
		nodeSuccessModelNode.innerHTML = result;
		var scripts = nodeSuccessModelNode.getElementsByTagName('script');
		for (var ix = 0; ix < scripts.length; ix++) {
			 jQuery.globalEval(scripts[ix].text);
		}
	});
};
	
/**
 * Visualizes a service success model and writes it to the output div.
 */
var visualize_service_success_model = function(){
	var modelName = successModelSelectNode.options[successModelSelectNode.selectedIndex].text;
	psLibrary.visualizeServiceSuccessModel(modelName, function(result){
		serviceSuccessModelNode.innerHTML = result;
		var scripts = serviceSuccessModelNode.getElementsByTagName('script');
		for (var ix = 0; ix < scripts.length; ix++) {
			 jQuery.globalEval(scripts[ix].text);
		}
	});
};

//Login by default
login();
