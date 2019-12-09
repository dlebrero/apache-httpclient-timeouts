# Apache Http Client and Asynchronous HTTP client timeouts explained

This code is the companion of the blog post [Apache Http Client and Asynchronous HTTP client timeouts explained in pictures](http://danlebrero.com/2019/12/11/apache-http-client-timeouts-config-production-asynchronous-http-client-pictures/)

This project uses Docker to create an environment with an HTTP client which queries an HTTP server, with a ToxiProxy in the middle to mess around with the behaviour of the HTTP server. 

## Usage

Docker should be installed.

To run:

     docker-compose up -d && docker-compose logs
     
Then connect to the REPL in port 47480.

To test the [Apache HTTP client](http://hc.apache.org), see [here](/src/apache_httpclient_timeouts/http_client.clj). The [Asynchronous HTTP Client](https://github.com/AsyncHttpClient/async-http-client) example is [here](/src/apache_httpclient_timeouts/async_http_client.clj)