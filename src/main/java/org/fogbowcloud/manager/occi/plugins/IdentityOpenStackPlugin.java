package org.fogbowcloud.manager.occi.plugins;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.occi.RequestHelper;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class IdentityOpenStackPlugin implements IdentityPlugin {

	public static final String DEFAULT_END_POINT_TOKENS = "http://127.0.0.1:5000/v3/auth/tokens/";
	private String endPoint;
	
	public IdentityOpenStackPlugin() {
		this.endPoint = DEFAULT_END_POINT_TOKENS;
	}
	
	public IdentityOpenStackPlugin(String endPoint) {
		this.endPoint = endPoint;
	}
	
	public boolean isValidToken(String token) {
		try {
			HttpClient httpCLient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(this.endPoint);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, token);
			httpGet.addHeader(OCCIHeaders.X_SUBJEC_TOKEN, token);
			HttpResponse response = httpCLient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED
					|| response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				return false;
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public String getUser(String token) {
		try {
			HttpClient httpCLient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(this.endPoint);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, token);
			httpGet.addHeader(OCCIHeaders.X_SUBJEC_TOKEN, token);
			HttpResponse response = httpCLient.execute(httpGet);
			String responseStr = EntityUtils.toString(response.getEntity(), 
					RequestHelper.UTF_8);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			return getUserNameUserFromJson(responseStr);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	private String getUserNameUserFromJson(String responseStr) {
		try {
			JSONTokener tokener = new JSONTokener(responseStr);
			JSONObject root = new JSONObject(tokener);
			root = (JSONObject) root.get("token");
			root = (JSONObject) root.get("user");

			return root.get("name").toString();
		} catch (JSONException e) {
			return null;
		}

	}
}
