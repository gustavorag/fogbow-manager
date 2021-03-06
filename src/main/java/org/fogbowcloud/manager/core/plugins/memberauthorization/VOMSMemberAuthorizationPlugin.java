package org.fogbowcloud.manager.core.plugins.memberauthorization;

import java.util.Properties;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.FederationMemberAuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.occi.model.Token;

public class VOMSMemberAuthorizationPlugin implements FederationMemberAuthorizationPlugin {

	private static final String PROP_PREFIX = "member_authorization_voms_";

	public static final String PROP_CHECK_FORWARDED_PRIVATE_KEY = PROP_PREFIX
			+ "check_forwarded_private_key";
	public static final String PROP_PATH_VOMSES = PROP_PREFIX + VomsIdentityPlugin.PROP_PATH_VOMSES;
	public static final String PROP_PATH_TRUST_ANCHORS = PROP_PREFIX
			+ VomsIdentityPlugin.PROP_PATH_TRUST_ANCHORS;
	public static final String PROP_PATH_VOMSDIR = PROP_PREFIX
			+ VomsIdentityPlugin.PROP_PATH_VOMSDIR;
	
	private final Properties properties;
	private final VomsIdentityPlugin innerPlugin;

	public VOMSMemberAuthorizationPlugin(Properties properties) {
		this.properties = properties;
		Properties identityPluginProperties = new Properties();
		identityPluginProperties.put(VomsIdentityPlugin.PROP_PATH_VOMSES,
				properties.getProperty(PROP_PATH_VOMSES));
		identityPluginProperties.put(VomsIdentityPlugin.PROP_PATH_TRUST_ANCHORS,
				properties.getProperty(PROP_PATH_TRUST_ANCHORS));
		identityPluginProperties.put(VomsIdentityPlugin.PROP_PATH_VOMSDIR,
				properties.getProperty(PROP_PATH_VOMSDIR));
		this.innerPlugin = new VomsIdentityPlugin(identityPluginProperties);
	}
	
	@Override
	public boolean canDonateTo(FederationMember member,
			Token requestingUserToken) {
		String checkForwardedPrivateKeyStr = properties.getProperty(PROP_CHECK_FORWARDED_PRIVATE_KEY);
		boolean checkPrivateKey = checkForwardedPrivateKeyStr != null && 
				Boolean.parseBoolean(checkForwardedPrivateKeyStr);
		return innerPlugin.isValid(requestingUserToken.getAccessId(), checkPrivateKey);
	}

	@Override
	public boolean canReceiveFrom(FederationMember member) {
		return true;
	}
}
