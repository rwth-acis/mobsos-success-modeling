/*
Copyright (c) 2014 Dominik Renzel, Peter de Lange, Alexander Ruppert, Advanced Community Information Systems (ACIS) Group,
Chair of Computer Science 5 (Databases & Information Systems), RWTH Aachen University, Germany
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

* Neither the name of the ACIS Group nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

function MobSOSSuccessModelingClient(endpointUrl){

	// care for trailing slash in endpoint URL
	if(endpointUrl.endsWith("/")){
		this._serviceEndpoint = endpointUrl.substr(0,endpointUrl.length-1);
	} else {
		this._serviceEndpoint = endpointUrl;
	}
	
	// remember last page for redirection after OpenID Connect login
	window.localStorage["last_resource"] = window.location.href;
};

MobSOSSuccessModelingClient.prototype.isAnonymous = function(){
	if (oidc_userinfo !== undefined){
		return false;
	} else {
		return true;
	}
};

MobSOSSuccessModelingClient.prototype.navigateTo = function(path){
	var rurl = window.location.href + "/" + path;
	
	if(!this.isAnonymous()){
		if(rurl.indexOf("\?") > 0){
			console.log("Authenticated request... appending token as additional query param");
			rurl += "&access_token=" + window.localStorage["access_token"];
		} else {
			console.log("Authenticated request... appending token as query");
			rurl += "?access_token=" + window.localStorage["access_token"];
		}
	} else {
		console.log("Anonymous request... ");
	}
	
	window.location.href = rurl;
};

MobSOSSuccessModelingClient.prototype.navigateAbsolute = function(rurl){
	
	if(!this.isAnonymous()){
		if(rurl.indexOf("\?") > 0){
			console.log("Authenticated request... appending token as additional query param");
			rurl += "&access_token=" + window.localStorage["access_token"];
		} else {
			console.log("Authenticated request... appending token as query");
			rurl += "?access_token=" + window.localStorage["access_token"];
		}
	} else {
		console.log("Anonymous request... ");
	}
	
	window.location.href = rurl;
};

MobSOSSuccessModelingClient.prototype.getNodes = function(callback) {
	this.sendRequest("GET",
		"nodes",
		null,
		"text/html",
		{"Accept":"application/json"},
		function(data, type){
			callback(data);
		},
		function(error){
			callback("Error! Message: " + error)
		}
	);
};

MobSOSSuccessModelingClient.prototype.getServices = function(callback){
	
	this.sendRequest("GET",
		"services",
		null,
		"text/html",
		{"Accept":"application/json"},
		function(data, type){
			callback(data);
		},
		function(error){
			callback("Error! Message: " + error)
		}
	);
	
};

MobSOSSuccessModelingClient.prototype.getCatalogs = function(callback){
	
	this.sendRequest("GET",
		"measureCatalogs",
		null,
		"text/html",
		{"Accept":"application/json"},
		function(data, type){
			callback(data);
		},
		function(error){
			callback("Error! Message: " + error)
		}
	);
	
};

MobSOSSuccessModelingClient.prototype.visualizeNodeSuccessModel = function(node,catalog, callback){
	this.sendRequest("POST",
		"visualize/nodeSuccessModel",
		"{\"nodeName\": \""+node+"\",\"updateModels\": \"true\",\"updateMeasures\": \"true\",\"catalog\": \""+catalog+"\"}",
		"application/json",
		{"Accept":"text/html"},
		function(result){callback(result)},
		function(result){callback(result)});
}

MobSOSSuccessModelingClient.prototype.getSuccessModels = function(serviceName, catalogName, callback){
	
	this.sendRequest("GET",
		"models?service="+serviceName+"&update=true&catalog="+catalogName,
		"",
		"text/html",
		{"Accept":"application/json"},
		function(data, type){
			callback(data);
		},
		function(error){
			callback("Error! Message: " + error)
		}
	);
	
};

MobSOSSuccessModelingClient.prototype.visualizeServiceSuccessModel = function(modelName, catalogName, callback){
	this.sendRequest("POST",
		"visualize/serviceSuccessModel",
		"{\"modelName\": \""+modelName+"\",\"updateModels\": \"true\",\"updateMeasures\": \"true\",\"catalog\": \""+catalogName+"\"}",
		"application/json",
		{"Accept":"text/html"},
		function(data, type){
			callback(data);
		},
		function(error){
			callback("Error! Message: " + error)
		}
	);
}

MobSOSSuccessModelingClient.prototype.getResourceMeta = function(id, callback, errorCallback){
	this.getResourcesMeta(function(data,type){
		console.log("Resources:" + data.length);	
		for(var i=0;i<data.length;i++){
			if(data[i].id === id){
				callback(data[i],type);	
			}	
		}
		errorCallback("No client found.");
	},errorCallback);
	
}

MobSOSSuccessModelingClient.prototype.getResourcesMeta = function(callback, errorCallback){
	
	//this.sendRequest("POST",
	//	"resource-meta",
	//	uri,
	//	"text/plain",
	//	{},
	//	callback,
	//	errorCallback);
	
	this.getUserInfo(function(data){console.log(data);},function(error){console.log(error);});	
	this.sendRequestExt("https://api.learning-layers.eu/o/oauth2",
		"GET",
		"api/clients",
		"",	
		"application/json",
		{"Accept":"application/json"},
		function(data,type) {
			//console.log(data);
			var pdata = [];
 
			for(var i=0;i<data.length;i++){
				//console.log("data id: "+ data[i].id);
				if(data[i].logoUri !== null){
					pdata.push({"id":data[i].clientId,"name":data[i].clientName,"logo":data[i].logoUri,"uri":data[i].logoUri});	
				} 	
			}
			console.log(pdata);	
			callback(pdata,type);
		},
			
		function(error) {console.log(error);console.log("Error!"); errorCallback(error);}	
	);
};

MobSOSSuccessModelingClient.prototype.getUserInfo = function(callback, errorCallback){
	
	this.sendRequest("GET",
		"userinfo",
		"",
		"",
		{"Accept":"application/json"},
		callback,
		errorCallback);
	
};

MobSOSSuccessModelingClient.prototype.sendRequestExt = function (ext_ep, method, relativePath, content, mime, customHeaders, callback, errorCallback) {
	var mtype = "text/plain; charset=UTF-8";
	if(mime !== 'undefined') {
		mtype = mime;	
	}
	var rurl = ext_ep + "/" + relativePath;
	
	if(!this.isAnonymous()){
		if(rurl.indexOf("\?") > 0){
			rurl += "&access_token=" + window.localStorage["access_token"];	
		} else {
			rurl += "?access_token=" + window.localStorage["access_token"];	
		}
	} else {
		console.log("Anonymous request.... " + rurl);	
	}
	
	var ajaxObj = {
		url: rurl,
		type: method.toUpperCase(),
		data: content,	
		contentType: mtype,
		crossDomain: true,
		headers: {"oidc_provider":oidc_server},
		error: function (xhr, errorType, error) {
			//console.log("Error in sendRequestExt");	
			//console.log(error);
			errorCallback(error);	
		},
		success: function(data, status, xhr){
			//console.log("Success in sendRequestExt");	
			var type = xhr.getResponseHeader("Content-Type");
			callback(data,type);		
		}	
	};
	
	if (customHeaders !== undefined && customHeaders !== null) {
		$.extend(ajaxObj.headers, customHeaders);
	} 
	
	$.ajax(ajaxObj);
}

MobSOSSuccessModelingClient.prototype.sendRequest = function(method, relativePath, content, mime, customHeaders, callback, errorCallback) {
	
	var mtype = "text/plain; charset=UTF-8"
	if(mime !== 'undefined'){
		mtype = mime;
	}
	
	var rurl = this._serviceEndpoint + "/" + relativePath;
	
	if(!this.isAnonymous()){
		if(rurl.indexOf("\?") > 0){
			//console.log("Authenticated request... appending token as additional query param");
			rurl += "&access_token=" + window.localStorage["access_token"];
		} else {
			//console.log("Authenticated request... appending token as query");
			rurl += "?access_token=" + window.localStorage["access_token"];
		}
	} else {
		console.log("Anonymous request..... " + relativePath);
	}
	
	var ajaxObj = {
		url: rurl, 
		type: method.toUpperCase(),
		data: content,
		contentType: mtype,
		crossDomain: true,
		headers: {"oidc_provider":oidc_server},

		error: function (xhr, errorType, error) {
			console.log(error);
			var errorText = error;
			if (xhr.responseText != null && xhr.responseText.trim().length > 0){
				errorText = xhr.responseText;
			}
			errorCallback(errorText);
		},
		success: function (data, status, xhr) {
			var type = xhr.getResponseHeader("Content-Type");
			callback(data, type);
		},
	};
	
	$.extend(ajaxObj.headers,{"X-Oidc-Client-Id":oidc_clientid});
   
	if (customHeaders !== undefined && customHeaders !== null) {
		$.extend(ajaxObj.headers, customHeaders);
	}
	
	$.ajax(ajaxObj);
};

String.prototype.endsWith = function(suffix) {
    return this.indexOf(suffix, this.length - suffix.length) !== -1;
};
