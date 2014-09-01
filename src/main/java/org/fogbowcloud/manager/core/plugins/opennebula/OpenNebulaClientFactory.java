package org.fogbowcloud.manager.core.plugins.opennebula;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;
import org.opennebula.client.template.TemplatePool;
import org.opennebula.client.user.User;
import org.opennebula.client.user.UserPool;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;

public class OpenNebulaClientFactory {
	
	private final static Logger LOGGER = Logger.getLogger(OpenNebulaClientFactory.class);

	public Client createClient(String accessId, String openNebulaEndpoint) {
		try {
			return new Client(accessId, openNebulaEndpoint);
		} catch (ClientConfigurationException e) {
			LOGGER.error("Exception while creating oneClient.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	public VirtualMachinePool createVirtualMachinePool(Client oneClient) {
		VirtualMachinePool vmPool = new VirtualMachinePool(oneClient);
		OneResponse response = vmPool.info();

		if (response.isError()) {
			LOGGER.error(response.getErrorMessage());
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
		return vmPool;
	}

	public VirtualMachine createVirtualMachine(Client oneClient, String instanceId) {
		VirtualMachine vm = new VirtualMachine(Integer.parseInt(instanceId), oneClient);
		OneResponse response = vm.info();
		
		if (response.isError()){
			String errorMessage = response.getErrorMessage(); 
			LOGGER.error(errorMessage);
			//Not authorized to perform
			if (errorMessage.contains("Not authorized")){
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED_USER);
			}
			//Error getting virtual machine
			throw new OCCIException(ErrorType.NOT_FOUND, errorMessage);
		}
		return vm;
	}

	public TemplatePool createTemplatePool(Client oneClient) {
		TemplatePool templatePool = new TemplatePool(oneClient);

		OneResponse response = templatePool.info();
		if (response.isError()) {
			LOGGER.error("Error while getting info about templates: "
					+ response.getErrorMessage());
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
		LOGGER.debug("Template pool length: " + templatePool.getLength());
		return templatePool;
	}

	public String allocateVirtualMachine(Client oneClient, String vmTemplate) {
		OneResponse response = VirtualMachine.allocate(oneClient, vmTemplate);
		if (response.isError()) {
			String errorMessage = response.getErrorMessage();
			LOGGER.error("Error while instatiating an instance from template: " + vmTemplate);
			LOGGER.error("Error message is: " + errorMessage);

			if (errorMessage.contains("limit") && errorMessage.contains("quota")) {
				throw new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
			}

			throw new OCCIException(ErrorType.BAD_REQUEST, errorMessage);
		}

		return response.getMessage();
	}

	public User createUser(Client oneClient) {
		String username ="";
		UserPool userpool = new UserPool(oneClient);
		userpool.info();
		String userId = "";
		for (User user : userpool) {
			if (username.equals(user.getName())){
				userId = user.getId();
				break;
			}
		}
		if (userId.isEmpty()){
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		User user = userpool.getById(Integer.parseInt(userId));
		user.info();		
		return user;
	}
}