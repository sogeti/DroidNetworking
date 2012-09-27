/**
 * Copyright 2012 Sogeti Sverige AB

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.sogeti.droidnetworking;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;

import org.apache.http.HttpHost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import com.sogeti.droidnetworking.NetworkOperation.CacheHandler;
import com.sogeti.droidnetworking.external.LruCache;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

public class NetworkEngine {
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;
    private static final int SOCKET_TIMEOUT = 5000;
    private static final int CONNECTION_TIMEOUT = 5000;

    private static final int DEFAULT_MEMORY_CACHE_SIZE = 2 * 1024 * 1024; // 2MB

    public enum HttpMethod {
        GET, POST, PUT, DELETE, HEAD
    }

    private static NetworkEngine networkEngine;
    private Context context;
    private Map<String, String> headers;
    private ExecutorService sharedNetworkQueue;

    private DefaultHttpClient httpClient;

    private LruCache<String, CacheEntry> memoryCache;
    private boolean useCache = false;
    private int memoryCacheSize = DEFAULT_MEMORY_CACHE_SIZE;

    public static synchronized NetworkEngine getInstance() {
        if (networkEngine == null) {
            networkEngine = new NetworkEngine();
        }

        return networkEngine;
    }

    public NetworkEngine() {
        sharedNetworkQueue = Executors.newFixedThreadPool(2);

        memoryCache = new LruCache<String, CacheEntry>(memoryCacheSize) {
            protected int sizeOf(final String key, final CacheEntry entry) {
                return entry.size();
            }
        };
    }

    public void init(final Context context) {
        init(context, null);
    }

    public void init(final Context context, final Map<String, String> headers) {
        this.context = context;

        // Setup HTTP
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), HTTP_PORT));

        // Setup HTTPS (accept all certificates)
        HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
        SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
        socketFactory.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
        schemeRegistry.register(new Scheme("https", socketFactory, HTTPS_PORT));

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, SOCKET_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, CONNECTION_TIMEOUT);

        ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager(params, schemeRegistry);

        httpClient = new DefaultHttpClient(connManager, params);

        if (headers == null) {
            this.headers = new HashMap<String, String>();
        } else {
            this.headers = headers;
        }

        if (!this.headers.containsKey("User-Agent")) {
            try {
                PackageInfo packageInfo = this.context.getPackageManager().getPackageInfo(
                        this.context.getPackageName(), 0);
                this.headers.put("User-Agent", packageInfo.packageName + "/" + packageInfo.versionName);
            } catch (NameNotFoundException e) {
                this.headers.put("User-Agent", "Unknown/0.0");
            }
        }
    }

    public void setProxyServer(final String host, final int port) {
    	HttpHost proxy = new HttpHost(host, port);
        httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
    }

    public NetworkOperation createOperationWithURLString(final String urlString) {
        return createOperationWithURLString(urlString, null, HttpMethod.GET);
    }

    public NetworkOperation createOperationWithURLString(final String urlString, final Map<String, String> params) {
        return createOperationWithURLString(urlString, params, HttpMethod.GET);
    }

    public NetworkOperation createOperationWithURLString(final String urlString, final Map<String, String> params,
            final HttpMethod httpMethod) {
        NetworkOperation operation = new NetworkOperation(urlString, params, httpMethod);

        prepareHeaders(operation);
        operation.setCacheHandler(new CacheHandler() {
            @Override
            public void cache(final NetworkOperation operation) {               
                CacheEntry entry = new CacheEntry(operation.getCacheHeaders(), operation.getResponseData());

                memoryCache.put(operation.getUniqueIdentifier(), entry);
            }
        });

        return operation;
    }

    public void prepareHeaders(final NetworkOperation operation) {
        operation.addHeaders(headers);
    }

    public void enqueueOperation(final NetworkOperation operation) {
        enqueueOperation(operation, false);
    }

    public void enqueueOperation(final NetworkOperation operation, final boolean forceReload) {        
    	executeOperation(operation, forceReload, true);
    }

    public void executeOperation(final NetworkOperation operation) {
        executeOperation(operation, false);
    }

    public void executeOperation(final NetworkOperation operation, final boolean forceReload) {      
        executeOperation(operation, forceReload, false);
    }

    private void executeOperation(final NetworkOperation operation, final boolean forceReload, final boolean enqueue) {      
        long expiryTimeInSeconds = 0;

        if (operation.isCachable() && useCache) {
        	if (!forceReload) {
		        CacheEntry entry = memoryCache.get(operation.getUniqueIdentifier());

		        if (entry != null) {
		        	operation.setCachedData(entry.getResponseData());

		        	if (entry.getCacheHeaders() != null) {
		        		SimpleDateFormat simpleDateFormat
		        			= new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
		        		String expiresOn = entry.getCacheHeaders().get("Expires");

		        		try {
			                Date expiresOnDate = simpleDateFormat.parse(expiresOn);
			                Date now = new Date();
			                expiryTimeInSeconds = expiresOnDate.getTime() - now.getTime();
			            } catch (ParseException e) {
			            	e.printStackTrace();
			            } finally {
			            	operation.updateOperation(entry.getCacheHeaders());
			            }
		        	}
		        }
        	}

        	if (expiryTimeInSeconds <= 0 || forceReload) {
        		if (enqueue) {
        			sharedNetworkQueue.submit(operation);
        		} else {
        			operation.execute();
        		}
        	} else {
        		operation.setFresh(true); // Cache is fresh enough

        		if (enqueue) {
        			sharedNetworkQueue.submit(operation);
        		} else {
        			operation.execute();
        		}
        	}
        } else {
        	if (enqueue) {
    			sharedNetworkQueue.submit(operation);
    		} else {
    			operation.execute();
    		}
        }
    }

    public DefaultHttpClient getHttpClient() {
        return httpClient;
    }

    public void clearCache() {
        memoryCache.evictAll();
    }

    public void setUseCache(boolean useCache) {
    	this.useCache = useCache;
    }

    public void setMemoryCacheSize(int memoryCacheSize) {
    	this.memoryCacheSize = memoryCacheSize;
    }

    public static class CacheEntry {
        private Map<String, String> cacheHeaders;
        private byte[] responseData;

        public CacheEntry(final Map<String, String> cacheHeaders, final byte[] responseData) {
            this.cacheHeaders = cacheHeaders;
            this.responseData = responseData;
        }

        public Map<String, String> getCacheHeaders() {
            return cacheHeaders;
        }

        public void setCacheHeaders(final Map<String, String> cacheHeaders) {
            this.cacheHeaders = cacheHeaders;
        }

        public byte[] getResponseData() {
            return responseData;
        }

        public void setResponseData(final byte[] responseData) {
            this.responseData = responseData;
        }

        public int size() {
            return responseData.length;
        }
    }
}
