package org.fogbowcloud.manager.core.plugins.prioritization.nof;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

public class TestNoFPrioritizationPlugin {

	private Properties properties;
	private AccountingPlugin accountingPlugin;

	@Before
	public void setUp(){
		properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_JID_KEY,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		properties.put("nof_trustworthy", "false");
		
		accountingPlugin = Mockito.mock(AccountingPlugin.class);
	}
	
	@Test
	public void testServedRequestsNull() {
		//mocking accounting
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		Assert.assertNull(nofPlugin.takeFrom("memberId", null, null));
	}
	
	@Test
	public void testEmptyServedRequests() {
		//mocking accounting
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		Assert.assertNull(nofPlugin.takeFrom("memberId", null, new ArrayList<Request>()));
	}
	
	@Test
	@Ignore
	public void testOneRemoteRequestWithDebtBigger() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(20);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);		
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		Request servedRequest = new Request("instanceToken", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "remoteMemberId");
		servedRequest.setInstanceId("instanceId");
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId("localMemberId");
		
		List<Request> requests = new ArrayList<Request>();
		requests.add(servedRequest);
		
		Assert.assertEquals(servedRequest, nofPlugin.takeFrom("member2", null, requests));
	}
}
