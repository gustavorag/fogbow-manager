package org.fogbowcloud.manager.core.plugins.egi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.occi.core.Token;

public class EgiImageStoragePlugin implements ImageStoragePlugin {

	private static final String IMAGE_EXTENSION = ".img";
	private static final String GLOBAL_ID_TAG = "GLOBAL_ID";
	private static final String DEFAULT_IMAGE_VERSION = "1.0";
	
	private static final Logger LOGGER = Logger.getLogger(ImageStoragePlugin.class);
	private static final Executor IMAGE_DOWNLOADER = Executors.newFixedThreadPool(5);
	
	private ComputePlugin computePlugin;
	private Set<String> pendingImageUploads = Collections.newSetFromMap(
			new ConcurrentHashMap<String, Boolean>());
	
	private final String marketPlaceBaseURL;
	private final String keystorePath;
	private final String tmpStorage;
	private final String keystorePassword;
	
	public EgiImageStoragePlugin(Properties properties) {
		this.marketPlaceBaseURL = properties.getProperty("image_storage_egi_base_url");
		this.keystorePath = properties.getProperty("image_storage_egi_keystore_path");
		this.keystorePassword = properties.getProperty("image_storage_egi_keystore_password");
		this.tmpStorage = properties.getProperty("image_storage_egi_tmp_storage");
	}
	
	public EgiImageStoragePlugin(Properties properties, ComputePlugin computePlugin) {
		this(properties);
		this.computePlugin = computePlugin;
	}
	
	@Override
	public String getImage(Token token, String globalId) {
		Map<String, String> tags = tags(globalId);
		String image = computePlugin.searchImage(token, tags);
		if (image == null) {
			scheduleImageDownload(token, globalId);
		}
		return image;
	}

	private static Map<String, String> tags(String globalId) {
		Map<String, String> tags = new HashMap<String, String>();
		tags.put(GLOBAL_ID_TAG, globalId);
		return tags;
	}

	private void scheduleImageDownload(final Token token, final String globalId) {
		if (pendingImageUploads.contains(globalId)) {
			return;
		}
		pendingImageUploads.add(globalId);
		IMAGE_DOWNLOADER.execute(new Runnable() {
			@Override
			public void run() {
				File downloadTempFile = downloadTempFile(globalId);
				if (downloadTempFile != null) {
					computePlugin.uploadImage(token, 
							downloadTempFile.getAbsolutePath(), 
							globalId, tags(globalId));
				}
				pendingImageUploads.remove(globalId);
			}
		});
	}
	
	private File downloadTempFile(final String globalId) {
		
		HttpClient httpclient = null;
		HttpEntity entity = null;
		try {
			httpclient = new DefaultHttpClient();
			injectKeystore(httpclient);
			HttpGet httpget = new HttpGet(createURL(globalId));
			HttpResponse response = httpclient.execute(httpget);
			entity = response.getEntity();
			if (entity != null) {
				File tempFile = File.createTempFile(globalId, 
						IMAGE_EXTENSION, new File(tmpStorage));
				InputStream instream = entity.getContent();
				FileUtils.copyInputStreamToFile(instream, tempFile);;
				instream.close();
				httpclient.getConnectionManager().shutdown();
				return tempFile;
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't download image file", e);
		} finally {
			if (entity != null) {
				try {
					entity.consumeContent();
				} catch (IOException e) {
					// Ignore this
				}
			}
			if (httpclient != null) {
				httpclient.getConnectionManager().shutdown();
			}
		}
		return null;
	}

	private void injectKeystore(HttpClient httpclient)
			throws Exception {
		if (keystorePath != null) {
			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(new FileInputStream(keystorePath), keystorePassword.toCharArray());
			SSLSocketFactory socketFactory = new SSLSocketFactory(ks);
			Scheme sch = new Scheme("https", socketFactory, 443);
			httpclient.getConnectionManager().getSchemeRegistry().register(sch);
		}
	}

	protected String createURL(String globalId) {
		String version = DEFAULT_IMAGE_VERSION;
		String imageName = globalId;
		int versionSeparatorIdx = globalId.lastIndexOf("/");
		if (versionSeparatorIdx >= 0) {
			version = globalId.substring(versionSeparatorIdx + 1);
			imageName = globalId.substring(0, versionSeparatorIdx);
		}
		return marketPlaceBaseURL + "/" + imageName + "/" + 
			version + "/" + imageName + IMAGE_EXTENSION;
	}
}
