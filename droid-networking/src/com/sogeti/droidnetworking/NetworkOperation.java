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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.message.BasicNameValuePair;

import com.sogeti.droidnetworking.NetworkEngine.HttpMethod;
import com.sogeti.droidnetworking.external.Base64;
import com.sogeti.droidnetworking.external.CachingInputStream;
import com.sogeti.droidnetworking.external.MD5;

import android.os.Handler;
import android.os.Message;

public class NetworkOperation implements Runnable {
    public static final int STATUS_COMPLETED = 0;
    public static final int STATUS_ERROR = 1;
    public static final int STATUS_CANCELLED = 2;
    public static final int STATUS_PENDING = 3;
    public static final int STATUS_EXECUTING = 4;
    public static final int STATUS_TIMEOUT = 5;

    private static final String LAST_MODIFIED = "Last-Modified";
    private static final String ETAG = "ETag";
    private static final String EXPIRES = "Expires";

    private static final int ONE_SECOND_IN_MS = 1000;

    private String urlString;
    private Map<String, String> headers;
    private Map<String, String> params;
    private HttpMethod httpMethod;
    private HttpResponse response;
    private HttpUriRequest request;
    private ResponseParser parser;
    private OperationListener listener;
    private int httpStatusCode;
    private boolean useGzip = true;
    private Map<String, String> cacheHeaders;
    private String username;
    private String password;
    private byte[] responseData;
    private byte[] cachedData;
    private CacheHandler cacheHandler;
    private boolean fresh = false;
    private int status;

    public interface ResponseParser {
        void parse(final InputStream is, final long size) throws IOException;
    }

    public interface OperationListener {
        void onCompletion(final NetworkOperation operation);

        void onError(final NetworkOperation operation);
    }

    public interface CacheHandler {
        void cache(final NetworkOperation operation);
    }

    public NetworkOperation() {
        this(null, null, null);
    }

    public NetworkOperation(final String urlString, final Map<String, String> params, final HttpMethod httpMethod) {
        this.urlString = urlString;
        this.httpMethod = httpMethod;
        this.params = new HashMap<String, String>();
        this.headers = new HashMap<String, String>();
        this.cacheHeaders = new HashMap<String, String>();

        status = STATUS_PENDING;

        if (params != null) {
            this.params.putAll(params);
        }
    }

    private int prepareRequest() {
        if (urlString == null || httpMethod == null) {
            return -1;
        }

        switch (httpMethod) {
            case GET :
                request = new HttpGet(urlString);
                break;
            case POST :
                request = new HttpPost(urlString);
                break;
            case PUT :
                request = new HttpPut(urlString);
                break;
            case DELETE :
                request = new HttpDelete(urlString);
                break;
            case HEAD :
                request = new HttpHead(urlString);
                break;
            default :
                break;
        }

        if (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT) {
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();

            for (String param : params.keySet()) {
                nameValuePairs.add(new BasicNameValuePair(param, params.get(param)));
            }

            try {
                if (httpMethod == HttpMethod.POST) {
                    ((HttpPost) request).setEntity(new UrlEncodedFormEntity(nameValuePairs));
                } else {
                    ((HttpPut) request).setEntity(new UrlEncodedFormEntity(nameValuePairs));
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        if (useGzip) {
            this.headers.put("Accept-Encoding", "gzip");
        }

        for (String header : headers.keySet()) {
            request.addHeader(header, headers.get(header));
        }

        return 0;
    }

    public void execute() {
        if (prepareRequest() != 0) {
            status = STATUS_ERROR;
            return;
        }

        status = STATUS_EXECUTING;

        if (!fresh) {
            try {
                response = NetworkEngine.getInstance().getHttpClient().execute(request);

                setCacheHeaders(response);

                httpStatusCode = response.getStatusLine().getStatusCode();

                if (response.getEntity() != null) {
                    HttpEntity entity = getDecompressingEntity(response.getEntity());

                    InputStream is = entity.getContent();

                    if (parser != null) {
                        CachingInputStream cis = new CachingInputStream(is);
                        parser.parse(cis, entity.getContentLength());

                        responseData = cis.getCache();

                        cis.close();
                    } else {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int read = 0;

                        while ((read = is.read(buffer, 0, buffer.length)) != -1) {
                            baos.write(buffer, 0, read);
                        }

                        responseData = baos.toByteArray();
                    }

                    if (httpStatusCode >= 200 && httpStatusCode < 300 && isCachable()) {
                        cachedData = null;

                        if (cacheHandler != null) {
                            cacheHandler.cache(this);
                        }
                    }

                    if (entity != null) {
                        entity.consumeContent();
                    }
                }
            } catch (ConnectTimeoutException e) {
                status = STATUS_TIMEOUT;
                return;
            } catch (SocketTimeoutException e) {
                status = STATUS_TIMEOUT;
                return;
            } catch (IOException e) {
                status = STATUS_ERROR;
                return;
            }
        }
        
        // Client and server errors
        if (httpStatusCode >= 400 && httpStatusCode < 600) {
            cachedData = null;
            status = STATUS_ERROR;
            return;
        }

    	if (cachedData != null) {
    		httpStatusCode = 200;

            if (parser != null) {
            	try {
            		InputStream is = new ByteArrayInputStream(cachedData);
            		parser.parse(is, cachedData.length);
            	} catch (IOException e) {
            		status = STATUS_ERROR;
    	            return;
                }
            }
        }
    	
    	status = STATUS_COMPLETED;
    }

    @Override
    public void run() {
        execute();

        if (Thread.currentThread().isInterrupted()) {
            handler.sendEmptyMessage(STATUS_CANCELLED);
        } else {
            handler.sendEmptyMessage(status);
        }
    }

    static class NetworkOperationHandler extends Handler {
        private NetworkOperation networkOperation;

        NetworkOperationHandler(final NetworkOperation networkOperation) {
            this.networkOperation = networkOperation;
        }

        @Override
        public void handleMessage(final Message message) {
            super.handleMessage(message);
            
            int status = message.what;
            
            if (status == STATUS_COMPLETED) {
                networkOperation.listener.onCompletion(networkOperation);
            } else {
                networkOperation.listener.onError(networkOperation);
            }
        }
    }

    private NetworkOperationHandler handler = new NetworkOperationHandler(this);

    public void setListener(final OperationListener listener) {
        this.listener = listener;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getResponseString() {
        return getResponseString("UTF-8");
    }

    public int getStatus() {
    	return status;
    }

    public String getUrlString() {
        return urlString;
    }

    public void setUrlString(final String urlString) {
        this.urlString = urlString;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(final HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getResponseString(final String encoding) {
        try {
            return new String(getResponseData(), encoding);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public HttpUriRequest getRequest() {
        return request;
    }

    public void addParams(final Map<String, String> params) {
        this.params.putAll(params);
    }

    public void addHeaders(final Map<String, String> headers) {
        this.headers.putAll(headers);
    }

    public ResponseParser getParser() {
        return parser;
    }

    public void setParser(final ResponseParser parser) {
        this.parser = parser;
    }

    public void setUseGzip(final boolean useGzip) {
        this.useGzip = useGzip;
    }

    public Map<String, String> getCacheHeaders() {
        return cacheHeaders;
    }

    public void setCacheHeaders(final Map<String, String> cacheHeaders) {
        this.cacheHeaders = cacheHeaders;
    }

    public byte[] getResponseData() {
        if (cachedData != null) {
            return cachedData;
        } else {
            return responseData;
        }
    }

    public void setCachedData(final byte[] cachedData) {
        this.cachedData = cachedData;
    }

    public boolean isCachedResponse() {
        return cachedData != null;
    }

    public CacheHandler getCacheHandler() {
        return cacheHandler;
    }

    public void setCacheHandler(final CacheHandler cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

	public boolean isFresh() {
		return fresh;
	}

	public void setFresh(final boolean fresh) {
		this.fresh = fresh;
	}

	private HttpEntity getDecompressingEntity(final HttpEntity entity) {
        Header header = entity.getContentEncoding();

        if (header != null) {
            HeaderElement[] codecs = header.getElements();

            for (int i = 0; i < codecs.length; i++) {
                if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                    return new GzipDecompressingEntity(entity);
                }
            }
        }

        return entity;
    }

    private static class GzipDecompressingEntity extends HttpEntityWrapper {
        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public InputStream getContent() throws IOException {
            InputStream is = wrappedEntity.getContent();

            return new GZIPInputStream(is);
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }

    private void setCacheHeaders(final HttpResponse response) {
        String lastModified = null;
        String eTag = null;
        String expiresOn = null;

        if (response.getFirstHeader(LAST_MODIFIED) != null) {
            lastModified = response.getFirstHeader(LAST_MODIFIED).getValue();
        }

        if (response.getFirstHeader(ETAG) != null) {
            eTag = response.getFirstHeader(ETAG).getValue();
        }

        if (response.getFirstHeader(EXPIRES) != null) {
            expiresOn = response.getFirstHeader(EXPIRES).getValue();
        }

        Header cacheControl = response.getFirstHeader("Cache-Control");

        if (cacheControl != null) {
            String[] cacheControlEntities = cacheControl.getValue().split(",");

            Date expiresOnDate = null;

            for (String subString : cacheControlEntities) {
                if (subString.contains("max-age")) {
                    String maxAge = null;
                    String[] array = subString.split("=");

                    if (array.length > 1) {
                        maxAge = array[1];
                    }

                    expiresOnDate = new Date();
                    expiresOnDate.setTime(expiresOnDate.getTime() + Integer.valueOf(maxAge) * ONE_SECOND_IN_MS);
                }

                if (subString.contains("no-cache")) {
                    expiresOnDate = new Date();
                }
            }

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            expiresOn = simpleDateFormat.format(expiresOnDate);
        }

        if (lastModified != null) {
            cacheHeaders.put(LAST_MODIFIED, lastModified);
        }

        if (eTag != null) {
            cacheHeaders.put(ETAG, eTag);
        }

        if (expiresOn != null) {
            cacheHeaders.put(EXPIRES, expiresOn);
        }
    }

    public void updateOperation(final Map<String, String> cacheHeaders) {
        String lastModified = cacheHeaders.get(LAST_MODIFIED);
        String eTag = cacheHeaders.get(ETAG);

        if (lastModified != null) {
            headers.put("IF-MODIFIED-SINCE", lastModified);
        }

        if (eTag != null) {
            headers.put("IF-NONE-MATCH", eTag);
        }
    }

    public boolean isCachable() {
        return httpMethod == HttpMethod.GET;
    }

    public String getUniqueIdentifier() {
        String str = httpMethod.toString() + " " + urlString;

        if (username != null && password != null) {
            str = str + " " + username + ":" + password;
        }

        return MD5.encodeString(str);
    }

    public void setBasicAuthenticationHeader(final String username, final String password) {
        this.username = username;
        this.password = password;

        String authStr = username + ":" + password;

        try {
            String authStrEncoded = Base64.encode(authStr.getBytes("UTF-8"));
            headers.put("Authorization", "Basic " + authStrEncoded);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
