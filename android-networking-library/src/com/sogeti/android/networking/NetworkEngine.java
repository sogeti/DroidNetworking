package com.sogeti.android.networking;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;

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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;

public class NetworkEngine {

    public enum HttpMethod {
        GET, POST, PUT, DELETE, HEAD
    }

    private static NetworkEngine networkEngine;
    private Context context;
    private String hostName;
    private Map<String, String> headers;
    private String apiPath;
    private int portNumber;
    private ExecutorService sharedNetworkQueue;

    private DefaultHttpClient httpClient;

    public static NetworkEngine getInstance() {
        if (networkEngine == null) {
            networkEngine = new NetworkEngine();
        }

        return networkEngine;
    }

    public NetworkEngine() {
        sharedNetworkQueue = Executors.newFixedThreadPool(2);
    }

    public void init(Context context, String hostName) {
        init(context, hostName, null, null);
    }

    public void init(Context context, String hostName, Map<String, String> headers) {
        init(context, hostName, null, headers);
    }

    public void init(Context context, String hostName, String apiPath, Map<String, String> headers) {
        this.hostName = hostName;
        this.context = context;
        this.apiPath = apiPath;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            // Use Apache HTTP client for before Gingerbread
            // http://android-developers.blogspot.se/2011/09/androids-http-clients.html

            // Setup HTTP
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

            // Setup HTTPS (accept all certificates)
            HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
            SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
            socketFactory.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
            schemeRegistry.register(new Scheme("https", socketFactory, 443));

            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(params, 5000);
            HttpConnectionParams.setSoTimeout(params, 5000);

            ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager(params, schemeRegistry);

            httpClient = new DefaultHttpClient(connManager, params);
        } else {
            // User URLConnection

            // Nothing to setup yet
        }

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

    public NetworkOperation createOperationWithPath(String path) {
        return createOperationWithPath(path, null, HttpMethod.GET, false);
    }

    public NetworkOperation createOperationWithPath(String path, Map<String, String> params) {
        return createOperationWithPath(path, params, HttpMethod.GET, false);
    }

    public NetworkOperation createOperationWithPath(String path, Map<String, String> params, HttpMethod httpMethod) {
        return createOperationWithPath(path, params, httpMethod, false);
    }

    public NetworkOperation createOperationWithPath(String path, Map<String, String> params, HttpMethod httpMethod,
            boolean ssl) {

        StringBuffer url = new StringBuffer();

        url.append(ssl ? "https://" : "http://");
        url.append(hostName);

        if (portNumber != 0)
            url.append(":" + portNumber);

        if (apiPath != null) {
            url.append(apiPath);
        }

        if (path != null) {
            url.append(path);
        }

        return createOperationWithURLString(url.toString(), params, httpMethod);
    }

    public NetworkOperation createOperationWithURLString(String urlString) {
        return createOperationWithURLString(urlString, null, HttpMethod.GET);
    }

    public NetworkOperation createOperationWithURLString(String urlString, Map<String, String> params) {
        return createOperationWithURLString(urlString, params, HttpMethod.GET);
    }

    public NetworkOperation createOperationWithURLString(String urlString, Map<String, String> params,
            HttpMethod httpMethod) {
        NetworkOperation operation = new NetworkOperation(urlString, params, httpMethod);

        prepareHeaders(operation);

        return operation;
    }

    public void prepareHeaders(NetworkOperation operation) {
        operation.addHeaders(headers);
    }

    public void enqueueOperation(NetworkOperation operation) {
        enqueueOperation(operation, false);
    }

    public void enqueueOperation(NetworkOperation operation, boolean forceReload) {
        sharedNetworkQueue.submit(operation);
    }

    public void executeOperation(NetworkOperation operation) {
        executeOperation(operation, false);
    }
    
    public void executeOperation(NetworkOperation operation, boolean forceReload) {
        operation.execute();
    }
    
    public DefaultHttpClient getHttpClient() {
        return httpClient;
    }

    public void setPortNumber(int portNumber) {
        this.portNumber = portNumber;
    }
}