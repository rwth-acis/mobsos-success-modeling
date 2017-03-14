/**
* Provision Service Application Script
* 
* @author Peter de Lange (lange@dbis.rwth-aachen.de)
*/


var psLibrary = new PS.ProvisionService();


	//Left
	nodeSelectNode = document.ps_visualizeNodeModel.NodeSelection,
	nodeSuccessModelNode = document.getElementById("ps_nodeSuccessModel"),
	
	//Right
	catalogSelectNode = document.ps_visualizeServiceModel.CatalogSelection,
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
		get_Catalogs();
	});
};

/**
 * Returns a list of Nodes and writes them into the corresponding select form.
 */
var get_Catalogs = function(){
	psLibrary.getCatalogs(function(result){
		if(result != null){
			result = result.replace(" ", "");
			result = result.substring(1,result.length-1);
			var catalogs = result.split(",");
			catalogSelectNode.options.length=0
			for (var i = 0; i < catalogs.length; i++) {
				var item = catalogs[i].replace(config.catalogPath,"");
				console.log(item);
				catalogSelectNode[i]=new Option(item);
			}
			$('.selectpicker').selectpicker('refresh');
		}
	});
};

/**
 * Returns a list of Nodes and writes them into the corresponding select form.
 */
var get_nodes = function(){
	psLibrary.getNodes(function(result){
		if(result != null){
			if($.isArray(result)){ //Ensure no (Ajax Client) error message is processed
				nodeSelectNode.options.length=0
				for (var i = 0; i < result.length; i++) {
					nodeSelectNode[i]=new Option(result[i]);
				}
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
			if($.isArray(result)){ //Ensure no (Ajax Client) error message is processed
				serviceSelectNode.options.length=0
				for (var i = 0; i < result.length; i++) {
					serviceSelectNode.options[i]=new Option(result[i]);
				}
			}
		}
	});
};

/**
 * Returns a list of service success models corresponding to the currently selected service.
 * Writes them to the corresponding select form.
 */
var get_success_models = function(){
	var length = successModelSelectNode.options.length;
	for (i = 0; i < length; i++) {
	  successModelSelectNode.options[i] = null;
	}
	successModelSelectNode.options[0]  = new Option("Success model not found");
	var serviceName = serviceSelectNode.options[serviceSelectNode.selectedIndex].text;
	var catalogName = config.catalogPath + catalogSelectNode.options[catalogSelectNode.selectedIndex].text;
	console.log(catalogName);
	psLibrary.getSuccessModels(serviceName, catalogName, function(result){
		console.log("sucModels: "+ result);
		if(result != null){
			if($.isArray(result)){ //Ensure no (Ajax Client) error message is processed
				successModelSelectNode.options.length=0
				for (var i = 0; i < result.length; i++) {
					successModelSelectNode.options[i]=new Option(result[i]);
				}
			}
		}
		$('.selectpicker').selectpicker('refresh');
	});
	$('.selectpicker').selectpicker('refresh');
};
	
/**
 * Visualizes a node success model and writes it to the output div.
 */
var visualize_node_success_model = function(){
	var nodeName = nodeSelectNode.options[nodeSelectNode.selectedIndex].text;
	var catalogName = config.catalogPath + catalogSelectNode.options[catalogSelectNode.selectedIndex].text;
	psLibrary.visualizeNodeSuccessModel(nodeName, catalogName, function(result){
		if(result.substr(0, 16) != "Error! Message: "){ //Ensure no (Ajax Client) error message is processed
			nodeSuccessModelNode.innerHTML = result;
			var scripts = nodeSuccessModelNode.getElementsByTagName('script');
			for (var ix = 0; ix < scripts.length; ix++) {
				 jQuery.globalEval(scripts[ix].text);
			}
		}
		else{
			nodeSuccessModelNode.innerHTML = "Sorry, something went wrong while visualizing the model (try again?)";
		}
	});
};
	
/**
 * Visualizes a service success model and writes it to the output div.
 */
var visualize_service_success_model = function(){
	var modelName = successModelSelectNode.options[successModelSelectNode.selectedIndex].text;
	var catalogName = config.catalogPath + catalogSelectNode.options[catalogSelectNode.selectedIndex].text;
	psLibrary.visualizeServiceSuccessModel(modelName, catalogName, function(result){
		if(result.substr(0, 16) != "Error! Message: "){ //Ensure no (Ajax Client) error message is processed
			serviceSuccessModelNode.innerHTML = result;
			var scripts = serviceSuccessModelNode.getElementsByTagName('script');
			for (var ix = 0; ix < scripts.length; ix++) {
				 jQuery.globalEval(scripts[ix].text);
			}
		}
		else{
			serviceSuccessModelNode.innerHTML = "Sorry, something went wrong while visualizing the model (try again?)";
		}
	});
};

//Login by default
login();
