package org.fogbowcloud.manager.occi;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.occi.instance.ComputeServerResource;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.member.MemberServerResource;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestServerResource;
import org.restlet.Application;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.routing.Router;
import org.restlet.util.Series;

public class OCCIApplication extends Application {

	private ManagerController managerFacade;

	public OCCIApplication(ManagerController facade) {
		this.managerFacade = facade;
	}

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach("/" + RequestConstants.TERM, RequestServerResource.class);
		router.attach("/" + RequestConstants.TERM + "/", RequestServerResource.class);
		router.attach("/" + RequestConstants.TERM + "/{requestId}", RequestServerResource.class);
		router.attach("/" + RequestConstants.COMPUTE_TERM, ComputeServerResource.class);
		router.attach("/" + RequestConstants.COMPUTE_TERM + "/", ComputeServerResource.class);
		router.attach("/" + RequestConstants.COMPUTE_TERM + "/{instanceId}", ComputeServerResource.class);
		router.attach("/members", MemberServerResource.class);
		router.attach("/members/{memberId}/quota", MemberServerResource.class);
		//TODO remove this endpoint
		router.attach("/token", TokenServerResource.class);
		router.attach("/-/", QueryServerResource.class);
		router.attach("/.well-known/org/ogf/occi/-/", QueryServerResource.class);
		router.attach("/usage", UsageServerResource.class);
		router.attach("/usage/{option}", UsageServerResource.class);
		router.attachDefault(new Restlet() {
			@Override
			public void handle(org.restlet.Request request, Response response) {
				normalizeBypass(request, response);
			}
		});
		return router;
	}
	
	@Override
	public void handle(org.restlet.Request request, Response response) {
		super.handle(request, response);
		
		/*
		 * The request will be bypassed only if response status was
		 * Method_NOT_ALLOWED and request path is not fogbow_request. Local
		 * private cloud does not treat fogbow_request requests.
		 */
		if (response.getStatus().getCode() == HttpStatus.SC_METHOD_NOT_ALLOWED
				&& !request.getOriginalRef().getPath().startsWith("/" + RequestConstants.TERM)) {
			normalizeBypass(request, response);
		}
	}

	@SuppressWarnings("unchecked")
	private void normalizeBypass(org.restlet.Request request, Response response) {
		Response newResponse = new Response(request);		
		normalizeHeadersForBypass(request);	
		
		bypass(request, newResponse);

		Series<org.restlet.engine.header.Header> responseHeaders = (Series<org.restlet.engine.header.Header>) newResponse
				.getAttributes().get("org.restlet.http.headers");
		if (responseHeaders != null) {
			// removing restlet default headers that will be added automatically
			responseHeaders.removeAll(HeaderConstants.HEADER_CONTENT_LENGTH);
			responseHeaders.removeAll(HeaderConstants.HEADER_CONTENT_TYPE);
			responseHeaders.removeAll(HeaderUtils.normalize(HeaderConstants.HEADER_CONTENT_TYPE));
			responseHeaders.removeAll(HeaderConstants.HEADER_DATE);
			responseHeaders.removeAll(HeaderConstants.HEADER_SERVER);
			responseHeaders.removeAll(HeaderConstants.HEADER_VARY);
			responseHeaders.removeAll(HeaderConstants.HEADER_ACCEPT_RANGES);
			newResponse.getAttributes().put("org.restlet.http.headers", responseHeaders);
		}
		response.setEntity(newResponse.getEntity());
		response.setStatus(newResponse.getStatus());
		response.setAttributes(newResponse.getAttributes());
	}

	public Token getToken(Map<String, String> attributesToken) {
		return managerFacade.getToken(attributesToken);
	}
	
	@SuppressWarnings("unchecked")
	public static void normalizeHeadersForBypass(org.restlet.Request request) {
		Series<Header> requestHeaders = (Series<Header>) request.getAttributes().get("org.restlet.http.headers");
		requestHeaders.add(OCCIHeaders.X_AUTH_TOKEN, requestHeaders.getFirstValue(HeaderUtils
				.normalize(OCCIHeaders.X_AUTH_TOKEN)));
		requestHeaders.removeFirst(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN));
	}

	
	public List<FederationMember> getFederationMembers(String accessId) {		
		return managerFacade.getRendezvousMembersInfo();
	}
	
	public FederationMember getFederationMemberQuota(String federationMemberId, String accessId) {		
		return managerFacade.getFederationMemberQuota(federationMemberId, accessId);
	}	

	public Request getRequest(String authToken, String requestId) {
		return managerFacade.getRequest(authToken, requestId);
	}

	public List<Request> createRequests(String federationAuthToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		return managerFacade.createRequests(federationAuthToken, categories, xOCCIAtt);
	}

	public List<Request> getRequestsFromUser(String authToken) {
		return managerFacade.getRequestsFromUser(authToken);
	}

	public void removeAllRequests(String authToken) {
		managerFacade.removeAllRequests(authToken);
	}

	public void removeRequest(String authToken, String requestId) {
		managerFacade.removeRequest(authToken, requestId);
	}

	public List<Instance> getInstances(String authToken) {
		return managerFacade.getInstances(authToken);
	}
	
	public List<Instance> getInstancesFullInfo(String authToken) {
		return managerFacade.getInstancesFullInfo(authToken);
	}

	public Instance getInstance(String authToken, String instanceId) {
		return managerFacade.getInstance(authToken, instanceId);
	}

	public void removeInstances(String authToken) {
		managerFacade.removeInstances(authToken);
	}

	public void removeInstance(String authToken, String instanceId) {
		managerFacade.removeInstance(authToken, instanceId);
	}

	public List<Resource> getAllResources(String authToken) {
		return managerFacade.getAllResouces(authToken);
	}

	/**
	 * This method will not be supported in next releases.
	 * @param request
	 * @param response
	 */
	@Deprecated
	public void bypass(org.restlet.Request request, Response response) {
		managerFacade.bypass(request, response);
	}

	public String getAuthenticationURI() {
		return managerFacade.getAuthenticationURI();
	}
	
	public Properties getProperties() {
		return managerFacade.getProperties();
	}
	
	public List<Flavor> getFlavorsProvided(){
		return managerFacade.getFlavorsProvided();
	}

	public List<ResourceUsage> getMembersUsage(String authToken) {
		return managerFacade.getMembersUsage(authToken);
	}

	public Map<String, Double> getUsersUsage(String authToken) {		
		return managerFacade.getUsersUsage(authToken);
	}

	public String getUser(String authToken) {
		return managerFacade.getUser(authToken);
	}
}
