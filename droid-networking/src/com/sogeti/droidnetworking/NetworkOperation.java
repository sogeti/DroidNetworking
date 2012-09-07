package com.sogeti.droidnetworking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.message.BasicNameValuePair;

import com.sogeti.droidnetworking.NetworkEngine.HttpMethod;

import android.os.Handler;
import android.os.Message;

public class NetworkOperation implements Runnable {
    private static final int STATUS_COMPLETED = 0;
    private static final int STATUS_ERROR = 1;
    private static final int STATUS_CANCELLED = 2;
    
    private static final String LAST_MODIFIED = "Last-Modified";
    private static final String ETAG = "ETag";
    private static final String EXPIRES = "Expires";

    protected String urlString;
    protected Map<String, String> headers;
    protected Map<String, String> params;
    protected HttpMethod httpMethod;
    protected HttpResponse response;
    protected HttpUriRequest request;
    protected ResponseParser parser;
    protected OperationListener listener;
    protected String responseString;
    protected int httpStatusCode;
    protected boolean useGzip = true;
    protected Map<String, String> cacheHeaders;

    public interface ResponseParser {
        void parse(final InputStream is) throws IOException;
    }

    public interface OperationListener {
        void onCompletion(final NetworkOperation operation);

        void onError();
    }

    public NetworkOperation(final String urlString, final Map<String, String> params, final HttpMethod httpMethod) {
        this.urlString = urlString;
        this.httpMethod = httpMethod;
        this.params = new HashMap<String, String>();
        this.headers = new HashMap<String, String>();
        this.cacheHeaders = new HashMap<String, String>();

        if (useGzip) {
            this.headers.put("Accept-Encoding", "gzip");
        }

        if (params != null) {
            this.params.putAll(params);
        }
    }

    public void execute() {
        switch (httpMethod) {
            case GET:
                request = new HttpGet(urlString);
                break;
            case POST:
                request = new HttpPost(urlString);
                break;
            case PUT:
                request = new HttpPut(urlString);
                break;
            case DELETE:
                request = new HttpDelete(urlString);
                break;
            case HEAD:
                request = new HttpHead(urlString);
                break;
            default:
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

            }
        }

        for (String header : headers.keySet()) {
            request.addHeader(header, headers.get(header));
        }

        try {
            response = NetworkEngine.getInstance().getHttpClient().execute(request);

            HttpEntity entity = getDecompressingEntity(response.getEntity());

            setCacheHeaders(response);

            httpStatusCode = response.getStatusLine().getStatusCode();

            InputStream is = entity.getContent();

            if (parser != null) {
                parser.parse(is);
            } else {
                responseString = convertStreamToString(is);
            }

            is.close();

            if (entity != null) {
                entity.consumeContent();
            }
        } catch (ClientProtocolException e) {

        } catch (IOException e) {

        }
    }

    @Override
    public void run() {
        execute();

        if (Thread.currentThread().isInterrupted()) {
            handler.sendEmptyMessage(STATUS_CANCELLED);
        } else {
            handler.sendEmptyMessage(STATUS_COMPLETED);
        }
    }
    
    static class NetworkOperationHandler extends Handler {
        WeakReference<NetworkOperation> ref;

        NetworkOperationHandler(NetworkOperation networkOperation) {
            this.ref = new WeakReference<NetworkOperation>(networkOperation);
        }

        @Override
        public void handleMessage(Message message) {
        	NetworkOperation networkOperation = ref.get();
            
        	int status = message.arg1;
    		
        	if (status == STATUS_COMPLETED) {
                networkOperation.listener.onCompletion(networkOperation);
            } else if (status == STATUS_ERROR) {
            	networkOperation.listener.onError();
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
        return responseString;
    }

    public HttpUriRequest getRequest() {
        return request;
    }

    public void addParams(final Map<String, String> params) {
        params.putAll(params);
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

	public void setCacheHeaders(Map<String, String> cacheHeaders) {
		this.cacheHeaders = cacheHeaders;
	}

	private String convertStreamToString(final InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        StringBuilder sb = new StringBuilder();

        String line = null;

        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }

        return sb.toString();
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

    private class GzipDecompressingEntity extends HttpEntityWrapper {
        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException {
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
                    expiresOnDate.setTime(Integer.valueOf(maxAge) * 1000);
                }

                if (subString.contains("no-cache")) {
                	expiresOnDate = new Date();
                }
            }

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, dd MMM yyyyy HH:mm:ss z");
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
    
    public void updateOperation(Map<String, String> cacheHeaders) {
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
}

