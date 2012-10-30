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

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
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
import com.sogeti.droidnetworking.external.diskcache.Charsets;
import com.sogeti.droidnetworking.external.diskcache.DiskLruCache;
import com.sogeti.droidnetworking.external.diskcache.StrictLineReader;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

public class NetworkEngine {
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;
    private static final int DEFAULT_SOCKET_TIMEOUT = 5000;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 5000;

    private static final int DEFAULT_MEMORY_CACHE_SIZE = 2 * 1024 * 1024; // 2MB
    private static final int DEFAULT_DISK_CACHE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int DISK_CACHE_VALUE_COUNT = 2;
    private static final int DISK_CACHE_VERSION = 1;
    private static final int DISK_CACHE_ENTRY_METADATA = 0;
    private static final int DISK_CACHE_ENTRY_BODY = 1;

    public enum HttpMethod {
        GET, POST, PUT, DELETE, HEAD
    }

    private static NetworkEngine networkEngine;
    private Context context;
    private Map<String, String> headers;
    private ExecutorService sharedNetworkQueue;

    private DefaultHttpClient httpClient;
    private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
    private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;

    private LruCache<String, CacheEntry> memoryCache;
    private DiskLruCache diskCache;
    private boolean useCache = false;
    private int memoryCacheSize = DEFAULT_MEMORY_CACHE_SIZE;
    private int diskCacheSize = DEFAULT_DISK_CACHE_SIZE;

    public static synchronized NetworkEngine getInstance() {
        if (networkEngine == null) {
            networkEngine = new NetworkEngine();
        }

        return networkEngine;
    }

    public void init(final Context context) {
        init(context, null);
    }

    public void init(final Context context, final Map<String, String> headers) {
        this.context = context;

        // Setup a queue for operations
        sharedNetworkQueue = Executors.newFixedThreadPool(2);

        // Init the memory cache, if the default memory cache size shouldn't be used, set the
        // size using setMemoryCacheSize before calling init
        if (memoryCacheSize > 0) {
        	memoryCache = new LruCache<String, CacheEntry>(memoryCacheSize) {
	            protected int sizeOf(final String key, final CacheEntry entry) {
	                return entry.size();
	            }
	        };
        } else {
        	memoryCache = null;
        }

        // Init the disk cache, if the default disk cache size shouldn't be used, set the
        // size using setDiskCacheSize before calling init
        if (diskCacheSize > 0) {
	        try {
				diskCache = DiskLruCache.open(context.getCacheDir(),
						DISK_CACHE_VERSION, DISK_CACHE_VALUE_COUNT, diskCacheSize);
			} catch (IOException e) {
				diskCache = null;
			}
        } else {
        	diskCache = null;
        }

        // Setup HTTP
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), HTTP_PORT));

        // Setup HTTPS (accept all certificates)
        HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
        SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
        socketFactory.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
        schemeRegistry.register(new Scheme("https", socketFactory, HTTPS_PORT));

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, connectionTimeout);
        HttpConnectionParams.setSoTimeout(params, socketTimeout);

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

                if (memoryCache != null) {
                	memoryCache.put(operation.getUniqueIdentifier(), entry);
                }

            	if (diskCache != null) {
            		DiskLruCache.Editor editor = null;
            		try {
                		editor = diskCache.edit(operation.getUniqueIdentifier());

                		if (editor != null) {
                    		entry.writeTo(editor);
                    		editor.commit();
                    	}
            		} catch (IOException e) {
    					editor = null;
            		}
            	}
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
        		CacheEntry entry = null;

        		if (memoryCache != null) {
        			entry = memoryCache.get(operation.getUniqueIdentifier());
        		}

		        if (entry == null && diskCache != null) {
		        	DiskLruCache.Snapshot snapshot = null;

					try {
						snapshot = diskCache.get(operation.getUniqueIdentifier());

						if (snapshot != null) {
			        		entry = new CacheEntry(snapshot);
			        		snapshot.close();
			        	}
					} catch (IOException e) {
						snapshot = null;
					}
		        }

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
    	if (memoryCache != null) {
    		memoryCache.evictAll();
    	}

        if (diskCache != null) {
        	try {
				diskCache.delete();
				diskCache = DiskLruCache.open(context.getCacheDir(),
						DISK_CACHE_VERSION, DISK_CACHE_VALUE_COUNT, diskCacheSize);
			} catch (IOException e) {
				diskCache = null;
			}
        }
    }

    public void setUseCache(final boolean useCache) {
    	this.useCache = useCache;
    }

    public void setMemoryCacheSize(final int memoryCacheSize) {
    	this.memoryCacheSize = memoryCacheSize;
    }

    public void setDiskCacheSize(final int diskCacheSize) {
    	this.diskCacheSize = diskCacheSize;
    }
    
    public void setConnectionTimeout(final int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    
    public void setSocketTimeout(final int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public static class CacheEntry {
        private Map<String, String> cacheHeaders;
        private byte[] responseData;

        public CacheEntry(final Map<String, String> cacheHeaders, final byte[] responseData) {
            this.cacheHeaders = cacheHeaders;
            this.responseData = responseData;
        }

        public CacheEntry(final DiskLruCache.Snapshot snapshot) throws IOException {
        	StrictLineReader reader = new StrictLineReader(snapshot.getInputStream(DISK_CACHE_ENTRY_METADATA),
        			Charsets.US_ASCII);

        	cacheHeaders = new HashMap<String, String>();

			int cacheHeaderCount = reader.readInt();

			for (int i = 0; i < cacheHeaderCount; i++) {
        		String key = reader.readLine();
        		String value = reader.readLine();
        		cacheHeaders.put(key, value);
            }

        	reader.close();

        	ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read = 0;

            InputStream in = snapshot.getInputStream(DISK_CACHE_ENTRY_BODY);

            while ((read = in.read(buffer, 0, buffer.length)) != -1) {
                baos.write(buffer, 0, read);
            }

            in.close();

            responseData = baos.toByteArray();
        }

        public void writeTo(final DiskLruCache.Editor editor) throws IOException {
            OutputStream out = editor.newOutputStream(DISK_CACHE_ENTRY_METADATA);
            Writer writer = new BufferedWriter(new OutputStreamWriter(out, Charsets.US_ASCII));

            writer.write(Integer.toString(cacheHeaders.size()) + '\n');

            for (String key : cacheHeaders.keySet()) {
            	writer.write(key + '\n');
            	writer.write(cacheHeaders.get(key) + '\n');
            }

            writer.close();

            InputStream in = new ByteArrayInputStream(responseData);
            byte[] buffer = new byte[1024];
            int read = 0;

            out = editor.newOutputStream(DISK_CACHE_ENTRY_BODY);

            while ((read = in.read(buffer, 0, buffer.length)) != -1) {
            	out.write(buffer, 0, read);
            }

            out.close();
            in.close();
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
