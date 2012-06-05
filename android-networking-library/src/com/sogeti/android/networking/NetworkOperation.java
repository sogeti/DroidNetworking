package com.sogeti.android.networking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
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

import com.sogeti.android.networking.NetworkEngine.HttpMethod;

import android.os.Handler;
import android.os.Message;

public class NetworkOperation implements Runnable {

    private static final int STATUS_COMPLETED = 0;
    private static final int STATUS_ERROR = 1;
    private static final int STATUS_CANCELLED = 2;

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
    
    public static interface ResponseParser {
        public void parse(final InputStream is) throws IOException;
    }

    public static interface OperationListener {
        public void onCompletion(NetworkOperation operation);

        public void onError();
    }

    public NetworkOperation(String urlString, Map<String, String> params, HttpMethod httpMethod) {
        this.urlString = urlString;
        this.httpMethod = httpMethod;
        this.params = new HashMap<String, String>();
        this.headers = new HashMap<String, String>();
        
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

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int status = msg.arg1;

            if (status == STATUS_COMPLETED) {
                listener.onCompletion(NetworkOperation.this);
            } else if (status == STATUS_ERROR) {
                listener.onError();
            }
        }
    };

    public void setListener(OperationListener listener) {
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

    public void addParams(Map<String, String> params) {
        params.putAll(params);
    }

    public void addHeaders(Map<String, String> headers) {
        this.headers.putAll(headers);
    }

    public ResponseParser getParser() {
        return parser;
    }

    public void setParser(ResponseParser parser) {
        this.parser = parser;
    }
    
    public void setUseGzip(boolean useGzip) {
        this.useGzip = useGzip;
    }
    
    private String convertStreamToString(InputStream is) throws IOException{
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        
        StringBuilder sb = new StringBuilder();
 
        String line = null;
       
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
       
        return sb.toString();
    }
    
    private HttpEntity getDecompressingEntity(HttpEntity entity) {
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
}
