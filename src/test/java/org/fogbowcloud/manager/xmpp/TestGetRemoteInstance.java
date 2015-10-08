package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetRemoteInstance {

	private static final String WRONG_TOKEN = "wrong";
	private static final String INSTANCE_OTHER_USER = "otherUser";

	private ManagerTestHelper managerTestHelper;

	@Before
	public void setUp() throws XMPPException {
		this.managerTestHelper = new ManagerTestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.managerTestHelper.shutdown();
	}

	private Instance createInstance() {
		Category category = new Category("term", "schema", "class");

		List<String> supportedAtt = new ArrayList<String>();
		supportedAtt.add("att1");

		List<String> actions = new ArrayList<String>();
		actions.add("actions1");

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(new Resource(category, supportedAtt, actions, "location", "title", "rel"));

		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("key", "value");

		Link link = new Link("linkname", attributes);
		List<Link> links = new ArrayList<Link>();
		links.add(link);

		return new Instance(DefaultDataTestHelper.INSTANCE_ID, resources, 
				attributes, links, InstanceState.RUNNING);
	}

	@Test
	public void testGetRemoteInstance() throws Exception {
		managerTestHelper.initializeXMPPManagerComponent(false);
		Instance instance = createInstance();

		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(
						Mockito.any(Token.class),
						Mockito.eq(DefaultDataTestHelper.INSTANCE_ID))).thenReturn(instance);

		Token token = new Token("anyvalue", OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		Request request = new Request("anyvalue", token, null, null, true, "");
		request.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		Instance remoteInstance = null;
		try {
			remoteInstance = ManagerPacketHelper.getRemoteInstance(request,
					managerTestHelper.createPacketSender());
		} catch (Exception e) {
			e.printStackTrace();
		}

		Assert.assertEquals(InstanceState.RUNNING, remoteInstance.getState());
		Assert.assertEquals(instance.getId(), remoteInstance.getId());
		Assert.assertEquals(instance.getAttributes(), remoteInstance.getAttributes());
		Assert.assertEquals(instance.getResources().get(0).toHeader(), remoteInstance
				.getResources().get(0).toHeader());
		Assert.assertEquals(instance.getLinks().get(0).getName(), remoteInstance.getLinks().get(0).getName());
	}
	
	@Test(expected=OCCIException.class)
	public void testGetRemoteInstaceNotFound() throws Exception {
		Request request = new Request("anyvalue", new Token(WRONG_TOKEN, OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), null, null, true, "");
		request.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		managerTestHelper.initializeXMPPManagerComponent(false);

		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND))
				.when(this.managerTestHelper.getComputePlugin())
				.getInstance(Mockito.any(Token.class), Mockito.anyString());

		ManagerPacketHelper.getRemoteInstance(request, managerTestHelper.createPacketSender());
	}

	@Test(expected = OCCIException.class)
	public void testGetRemoteInstanceUnauthorized() throws Exception {
		Token token = new Token(WRONG_TOKEN, OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		Request request = new Request("anyvalue", token, null, null, true, "");
		request.setInstanceId(INSTANCE_OTHER_USER);
		request.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		managerTestHelper.initializeXMPPManagerComponent(false);

		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED))
				.when(this.managerTestHelper.getComputePlugin())
				.getInstance(Mockito.eq(token),
						Mockito.eq(INSTANCE_OTHER_USER));

		ManagerPacketHelper.getRemoteInstance(request, managerTestHelper.createPacketSender());
	}
}