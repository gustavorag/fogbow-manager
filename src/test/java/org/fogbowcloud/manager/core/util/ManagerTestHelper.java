package org.fogbowcloud.manager.core.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.CertificateHandlerHelper;
import org.fogbowcloud.manager.core.DefaultMemberValidator;
import org.fogbowcloud.manager.core.FederationMemberValidator;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.core.ssh.SSHTunnel;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.jamppa.client.XMPPClient;
import org.jamppa.component.PacketSender;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.mockito.Mockito;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

public class ManagerTestHelper extends DefaultDataTestHelper {

	private ManagerXmppComponent managerXmppComponent;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private Token defaultToken;
	private FakeXMPPServer fakeServer = new FakeXMPPServer();

	public ManagerTestHelper() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OpenStackIdentityPlugin.TENANT_ID_KEY, "tenantId_r4fci3qhbcy3b");
		this.defaultToken = new Token(ACCESS_TOKEN_ID, USER_NAME, TOKEN_FUTURE_EXPIRATION,
				tokenAttributes);
	}

	public ResourcesInfo getResources() throws CertificateException, IOException {
		List<Flavor> flavours = new LinkedList<Flavor>();
		flavours.add(new Flavor("small", "cpu", "mem", 2));
		flavours.add(new Flavor("small", "cpu", "mem", 3));
		ResourcesInfo resources = new ResourcesInfo("abc", "value1", "value2", "value3", "value4",
				flavours, getCertificate());
		return resources;
	}

	public IQ createWhoIsAliveResponse(ArrayList<FederationMember> aliveIds, IQ iq)
			throws CertificateException, IOException {
		IQ resultIQ = IQ.createResultIQ(iq);
		Element queryElement = resultIQ.getElement().addElement("query", WHOISALIVE_NAMESPACE);
		for (FederationMember rendezvousItem : aliveIds) {
			Element itemEl = queryElement.addElement("item");
			itemEl.addAttribute("id", rendezvousItem.getResourcesInfo().getId());
			itemEl.addElement("cert").setText(
					CertificateHandlerHelper.getBase64Certificate(getProperties()));
			Element statusEl = itemEl.addElement("status");
			statusEl.addElement("cpu-idle").setText(rendezvousItem.getResourcesInfo().getCpuIdle());
			statusEl.addElement("cpu-inuse").setText(
					rendezvousItem.getResourcesInfo().getCpuInUse());
			statusEl.addElement("mem-idle").setText(rendezvousItem.getResourcesInfo().getMemIdle());
			statusEl.addElement("mem-inuse").setText(
					rendezvousItem.getResourcesInfo().getMemInUse());

			List<Flavor> flavours = rendezvousItem.getResourcesInfo().getFlavours();
			for (Flavor f : flavours) {
				Element flavorElement = statusEl.addElement("flavor");
				flavorElement.addElement("name").setText(f.getName());
				flavorElement.addElement("cpu").setText(f.getCpu());
				flavorElement.addElement("mem").setText(f.getMem());
				flavorElement.addElement("capacity").setText(f.getCapacity().toString());
			}
			statusEl.addElement("cert");
			statusEl.addElement("updated").setText(
					String.valueOf(rendezvousItem.getFormattedTime()));
		}
		return resultIQ;
	}

	public XMPPClient createXMPPClient() throws XMPPException {

		XMPPClient xmppClient = Mockito.spy(new XMPPClient(CLIENT_ADRESS, CLIENT_PASS, SERVER_HOST,
				SERVER_CLIENT_PORT));
		fakeServer.connect(xmppClient);
		xmppClient.process(false);

		return xmppClient;
	}

	public PacketSender createPacketSender() throws XMPPException {
		final XMPPClient xmppClient = createXMPPClient();
		PacketSender sender = new PacketSender() {
			@Override
			public Packet syncSendPacket(Packet packet) {
				PacketFilter responseFilter = new PacketIDFilter(packet.getID());
				PacketCollector response = xmppClient.getConnection().createPacketCollector(
						responseFilter);
				xmppClient.send(packet);
				Packet result = response.nextResult(5000);
				response.cancel();
				return result;
			}

			@Override
			public void sendPacket(Packet packet) {
				xmppClient.send(packet);
			}
		};
		return sender;
	}

	public ComputePlugin getComputePlugin() {
		return computePlugin;
	}

	public IdentityPlugin getIdentityPlugin() {
		return identityPlugin;
	}

	@SuppressWarnings("unchecked")
	public ManagerXmppComponent initializeXMPPManagerComponent(boolean init) throws Exception {

		this.computePlugin = Mockito.mock(ComputePlugin.class);
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);

		Properties properties = new Properties();
		properties.put("federation_user_name", "fogbow");
		properties.put("federation_user_password", "fogbow");
		properties.put("federation_user_tenant_name", "fogbow");
		properties.put("xmpp_jid", "manager.test.com");

		ManagerController managerFacade = new ManagerController(properties);
		managerFacade.setComputePlugin(computePlugin);
		managerFacade.setIdentityPlugin(identityPlugin);
		FederationMemberValidator validator = new DefaultMemberValidator();
		managerFacade.setValidator(validator);

		managerXmppComponent = Mockito.spy(new ManagerXmppComponent(MANAGER_COMPONENT_URL,
				MANAGER_COMPONENT_PASS, SERVER_HOST, SERVER_COMPONENT_PORT, managerFacade));

		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				getResources());

		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenReturn(defaultToken);

		managerXmppComponent.setDescription("Manager Component");
		managerXmppComponent.setName("Manager");
		managerXmppComponent.setRendezvousAddress(CLIENT_ADRESS + SMACK_ENDING);
		managerXmppComponent.getManagerFacade().setSSHTunnel(Mockito.mock(SSHTunnel.class));
		fakeServer.connect(managerXmppComponent);
		managerXmppComponent.process();
		if (init) {
			managerXmppComponent.init();
		}
		return managerXmppComponent;
	}

	@SuppressWarnings("unchecked")
	public ManagerXmppComponent initializeLocalXMPPManagerComponent() throws Exception {

		this.computePlugin = Mockito.mock(ComputePlugin.class);
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);

		Properties properties = new Properties();
		properties.put("federation_user_name", "fogbow");
		properties.put("federation_user_password", "fogbow");
		properties.put("xmpp_jid", "manager.test.com");

		FederationMemberValidator validator = new DefaultMemberValidator();
		ManagerController managerFacade = new ManagerController(properties);
		managerFacade.setComputePlugin(computePlugin);
		managerFacade.setIdentityPlugin(identityPlugin);
		managerFacade.setValidator(validator);

		managerXmppComponent = Mockito.spy(new ManagerXmppComponent(MANAGER_COMPONENT_URL,
				MANAGER_COMPONENT_PASS, SERVER_HOST, SERVER_COMPONENT_PORT, managerFacade));

		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				getResources());

		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenReturn(defaultToken);
		
		managerXmppComponent.setDescription("Manager Component");
		managerXmppComponent.setName("Manager");
		managerXmppComponent.setRendezvousAddress(CLIENT_ADRESS + SMACK_ENDING);
		fakeServer.connect(managerXmppComponent);
		managerXmppComponent.process();
		return managerXmppComponent;
	}

	public IQ CreateImAliveResponse(IQ iq) {
		IQ response = IQ.createResultIQ(iq);
		return response;
	}

	public void shutdown() throws ComponentException {
		fakeServer.disconnect(managerXmppComponent.getJID().toBareJID());
	}

	@SuppressWarnings("unchecked")
	public List<FederationMember> getItemsFromIQ(Packet response) throws CertificateException,
			IOException {
		Element queryElement = response.getElement().element("query");
		Iterator<Element> itemIterator = queryElement.elementIterator("item");
		ArrayList<FederationMember> aliveItems = new ArrayList<FederationMember>();

		while (itemIterator.hasNext()) {
			Element itemEl = itemIterator.next();
			Attribute id = itemEl.attribute("id");
			Element statusEl = itemEl.element("status");
			X509Certificate cert = CertificateHandlerHelper.parseCertificate(itemEl.element("cert")
					.getText());
			String cpuIdle = statusEl.element("cpu-idle").getText();
			String cpuInUse = statusEl.element("cpu-inuse").getText();
			String memIdle = statusEl.element("mem-idle").getText();
			String memInUse = statusEl.element("mem-inuse").getText();
			List<Flavor> flavoursList = new LinkedList<Flavor>();
			Iterator<Element> flavourIterator = itemEl.elementIterator("flavor");
			while (flavourIterator.hasNext()) {
				Element flavour = itemIterator.next();
				String name = flavour.element("name").getText();
				String cpu = flavour.element("cpu").getText();
				String mem = flavour.element("mem").getText();
				int capacity = Integer.parseInt(flavour.element("capacity").getText());
				Flavor flavor = new Flavor(name, cpu, mem, capacity);
				flavoursList.add(flavor);
			}

			ResourcesInfo resources = new ResourcesInfo(id.getValue(), cpuIdle, cpuInUse, memIdle,
					memInUse, flavoursList, cert);
			FederationMember item = new FederationMember(resources);
			aliveItems.add(item);
		}
		return aliveItems;
	}

	public Properties getProperties() throws IOException {
		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(CONFIG_PATH);
		properties.load(input);
		return properties;
	}

	public Properties getProperties(String path) throws IOException {
		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(path);
		properties.load(input);
		return properties;
	}

	public X509Certificate getCertificate() throws CertificateException, IOException {
		return CertificateHandlerHelper.getCertificate(getProperties());
	}

	@SuppressWarnings("unchecked")
	public ManagerController createDefaultManagerController() {
		Properties properties = new Properties();
		properties.put("federation_user_name", DefaultDataTestHelper.MANAGER_COMPONENT_URL);
		properties.put("federation_user_name", DefaultDataTestHelper.USER_NAME);
		properties.put("federation_user_password", DefaultDataTestHelper.USER_PASS);
		properties.put("federation_user_tenant_name", DefaultDataTestHelper.TENANT_NAME);
		properties.put("scheduler_period", DefaultDataTestHelper.SCHEDULER_PERIOD.toString());
		properties
				.put("instance_monitoring_period", Long.toString(DefaultDataTestHelper.LONG_TIME));
		ManagerController managerController = new ManagerController(properties, 
				Mockito.mock(ScheduledExecutorService.class));

		// mocking compute
		computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		// mocking identity
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.ACCESS_TOKEN_ID)).thenReturn(
				defaultToken);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		return managerController;
	}

	public Token getDefaultToken() {
		return defaultToken;
	}

}
