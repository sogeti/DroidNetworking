DroidNetworking
===============

Most Android apps need to use HTTP to send and receive data. There are many options for network communication in Android. Google recommends using the Apache Http Client for Eclair (2.1) and Froyo (2.2). For Gingerbread (2.3) and newer the HttpURLConnection is recommended. However response caching was not introduced in the HttpURLConnection until Ice Cream Sandwich (4.0) was released.

DroidNetworking is a network library built on top of the [Apache Http Client](http://developer.android.com/reference/org/apache/http/client/HttpClient.html). It has support for response caching, authentication, HTTP and HTTPS and many other features. Best of all, DroidNetworking can be used on Eclair (2.1) and newer. It has a simple API which reduces the amount of code needed for network communication.

[![Build Status](https://sogetise.ci.cloudbees.com/buildStatus/icon?job=DroidNetworking)](https://sogetise.ci.cloudbees.com/job/DroidNetworking/)

Features
--------
- HTTP and HTTPS
- Compatible with **Android 2.1**  (API level 7) and later
- Make **asynchronous** or **synchronous** HTTP requests
- GET, POST, PUT, DELETE and HEAD requests supported
- Get the response as a **string**, **byte array** or **input stream**
- HTTP requests happens in **a background thread**
- Requests use a **threadpool** to limit concurrent resource usage
- Automatic **gzip** response decoding support
- Supports **Basic Authentication**
- Transparent HTTP **response cache**
- Multipart/form-data support

Usage
--------
See the following blog post for examples on how to use DroidNetworking: [Introducing DroidNetworking - A network library for Android](http://www.martindahl.se/2012/11/introducing-droidnetworking-network.html)

Build
--------
DroidNetworking is now using Gradle. If you have Android Studio or Gradle installed on your computer you can type ./gradlew makeJar to build DroidNetworking and create a jar. The jar can be found in droid-networking/build/libs. The latest jar can also be downloaded [here](https://s3.amazonaws.com/droid-networking/droid-networking-20140312.jar)

License
--------
Apache License, Version 2.0
http://www.apache.org/licenses/LICENSE-2.0

[![Built on CloudBees](http://www.cloudbees.com/sites/default/files/Button-Built-on-CB-1.png)](http://www.cloudbees.com)
