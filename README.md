DroidNetworking
===============

A networking library for Android that is built on top of the [HttpClient](http://developer.android.com/reference/org/apache/http/client/HttpClient.html).

Features
--------
- Compatible with **Android 2.1**  (API level 7) and later
- Make **asynchronous** or **synchronous** HTTP requests
- GET, POST, PUT, DELETE and HEAD requests supported
- Get the response as a **string** or **input stream**
- HTTP requests happens in **a background thread**
- Requests use a **threadpool** to limit concurrent resource usage
- Automatic **gzip** response decoding support
- HTTP 1.1 **cache** *(under development)*

Usage
--------
A simple synchronous GET request. Start by initializing the NetworkEngine:

``NetworkEngine.getInstance().init(this)``

Create a NetworkOperation with an URL string:

``NetworkOperation operation = NetworkEngine.getInstance().createOperationWithURLString("http://www.github.com");``
 
Execute the operation synchronously:

``NetworkEngine.getInstance().executeOperation(operation);``

Get the status code from the operation:

``operation.getHttpStatusCode();``

Get the response string from the operation:

``operation.getResponseString();``

More examples
--------
Check out the test cases for more examples how to use the DroidNetworking library.

License
--------
TBD