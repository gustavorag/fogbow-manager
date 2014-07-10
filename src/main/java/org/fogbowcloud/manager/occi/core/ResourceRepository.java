package org.fogbowcloud.manager.occi.core;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;

public class ResourceRepository {
	
	private static final Logger LOGGER = Logger.getLogger(ResourceRepository.class);
	private static final String OS_TPL_OCCI_SCHEME = "http://schemas.ogf.org/occi/infrastructure#os_tpl";
	private static final String RESOURCE_TPL_OCCI_SCHEME = "http://schemas.ogf.org/occi/infrastructure#resource_tpl";
	private static ResourceRepository instance;
	private static final String FOGBOWCLOUD_ENDPOINT = "http://localhost:8182";
	List<Resource> resources = new ArrayList<Resource>();
	
	public static ResourceRepository getInstance(){
		if (instance == null) {
			instance = new ResourceRepository();
		}
		return instance;
	}
	
	private ResourceRepository(){
		Resource fogbowRequest = new Resource(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS, RequestAttribute.getValues(), new ArrayList<String>(),
				FOGBOWCLOUD_ENDPOINT + "/request/", "Request new Instances", "");
		
		//size flavors
		Resource fogbowSmallFlavor = new Resource(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/small/",
				"Small Flavor", RESOURCE_TPL_OCCI_SCHEME);
		Resource fogbowMediumFlavor = new Resource(RequestConstants.MEDIUM_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/medium/",
				"Medium Flavor", RESOURCE_TPL_OCCI_SCHEME);
		Resource fogbowLargeFlavor = new Resource(RequestConstants.LARGE_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/large/",
				"Large Flavor", RESOURCE_TPL_OCCI_SCHEME);
		//TODO add actions	

		resources.add(fogbowRequest);
		resources.add(fogbowSmallFlavor);
		resources.add(fogbowMediumFlavor);
		resources.add(fogbowLargeFlavor);
	}
		
	public List<Resource> getAll() {
		return resources;
	}
	
	public void addImageResource(String imageName){
		Resource imageResource = new Resource(imageName, RequestConstants.TEMPLATE_OS_SCHEME,
				RequestConstants.MIXIN_CLASS, new ArrayList<String>(), new ArrayList<String>(),
				FOGBOWCLOUD_ENDPOINT + "/" + imageName + "/", imageName + " image", OS_TPL_OCCI_SCHEME);
		if (!resources.contains(imageResource)){
			LOGGER.debug("Adding image resource: " + imageResource.toHeader());
			resources.add(imageResource);
		}
	}

	public List<Resource> get(List<Category> categories) {
		List<Resource> allResources = getAll();
		List<Resource> requestResources = new ArrayList<Resource>();
		for (Category requestCategory : categories) {
			for (Resource resource : allResources) {
				if (resource.matches(requestCategory)) {
					requestResources.add(resource);
					break;
				}
			}
		}
		return requestResources;
	}

	public Resource get(String term) {
		List<Resource> allResources = getAll();
		for (Resource resource : allResources) {
			if (resource.getCategory().getTerm().equals(term)) {
				return resource;
			}
		}
		return null;
	}
	
	/**
	 * To be used only by tests
	 */	
	protected void reset(){
		instance = null;
	}
}
