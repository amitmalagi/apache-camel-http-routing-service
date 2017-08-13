package com.isha.routes;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.BuilderSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.http.conn.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ServiceRoutes extends RouteBuilder {

    private static final transient Logger LOG = LoggerFactory.getLogger(ServiceRoutes.class);
    
    @Override
    public void configure() throws Exception {

        LOG.info("Starting routes");
        
        
        
        
        //To simulate this exception, change the value of email or passwd fields in http producer endpoint
        onException(HttpOperationFailedException.class)
        .handled(true)
        .process(new Processor() {
            public void process(Exchange exchange) {
                // copy the caused exception values to the exchange as we want the response in the regular exchange
                // instead as an exception that will get thrown and thus the route breaks
                HttpOperationFailedException cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, HttpOperationFailedException.class);
                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, cause.getStatusCode());
                
                Map<String, String> headersMap = cause.getResponseHeaders();
                for (Map.Entry<String, String> entry : headersMap.entrySet()) {
                    exchange.getOut().setHeader(entry.getKey(), entry.getValue());                    
                }
                
                exchange.getOut().setBody(cause.getResponseBody());
            }
        });
        //.marshal().json(JsonLibrary.Jackson, ErrorResponse.class);
        
        //To simulate connection timeout exception, either change the provider hostname or port and send a request from the client
        onException(HttpHostConnectException.class)
        .maximumRedeliveries(4)
        .backOffMultiplier(2)
        .redeliveryDelay(100)
        .maximumRedeliveryDelay(5000)
        .useExponentialBackOff()
        .handled(true)
        .process(new Processor() {
            public void process(Exchange exchange) {
                // copy the caused exception values to the exchange as we want the response in the regular exchange
                // instead as an exception that will get thrown and thus the route breaks
            	HttpHostConnectException cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, HttpHostConnectException.class);
                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 503);
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                
                ErrorResponse errorResponse = new ErrorResponse(
                		"SERVICE_UNAVAILABLE", cause.getLocalizedMessage(), Arrays.asList(cause.getMessage()));
                
                exchange.getOut().setBody(errorResponse);
            }
        })
        .marshal().json(JsonLibrary.Jackson, ErrorResponse.class);
        
        //To simulate socket timeout exception, set httpClient.SocketTimeout option in http producer endpoint to a lesser value than
        //value of the delay() component in the mock http provider endpoint
        onException(SocketTimeoutException.class)
        .maximumRedeliveries(4)
        .backOffMultiplier(2)
        .redeliveryDelay(100)
        .maximumRedeliveryDelay(5000)
        .useExponentialBackOff()
        .handled(true)
        .process(new Processor() {
            public void process(Exchange exchange) {
                // copy the caused exception values to the exchange as we want the response in the regular exchange
                // instead as an exception that will get thrown and thus the route breaks
            	SocketTimeoutException cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, SocketTimeoutException.class);
                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 503);
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                
                ErrorResponse errorResponse = new ErrorResponse(
                		"SERVICE_UNAVAILABLE", cause.getLocalizedMessage(), Arrays.asList(cause.getMessage()));
                
                exchange.getOut().setBody(errorResponse);
            }
        })
        .marshal().json(JsonLibrary.Jackson, ErrorResponse.class);
        
        onException(Exception.class)
        .maximumRedeliveries(4)
        .backOffMultiplier(2)
        .redeliveryDelay(100)
        .maximumRedeliveryDelay(5000)
        .useExponentialBackOff()
        .handled(true)
        .process(new Processor() {
            public void process(Exchange exchange) {
                // copy the caused exception values to the exchange as we want the response in the regular exchange
                // instead as an exception that will get thrown and thus the route breaks
            	Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                
                ErrorResponse errorResponse = new ErrorResponse(
                		"INTERNAL_SERVER_ERROR", cause.toString(), Arrays.asList(cause.toString()));
                
                exchange.getOut().setBody(errorResponse);
            }
        })
        .marshal().json(JsonLibrary.Jackson, ErrorResponse.class);
        
        //Content based router to providers
        restConfiguration()
        .component("jetty")
        .host("localhost")
        .port(8080)
        .bindingMode(RestBindingMode.auto);
 
        rest("/router")
                .post("/signin")
                .type(SigninCredentials.class)
                .outType(SigninResponse.class)
                .to("direct:signinRoutes");
        
        from("direct:signinRoutes")
        .choice()
        	.when().simple("${body.provider} == 'providerA'")
        		.to("direct:providerARoute")
        	.when().simple("${body.provider} == 'providerB'")
        		.to("direct:providerBRoute")
        	.when().simple("${body.provider} == 'providerC'")
        		.to("direct:providerCRoute")
        	.otherwise()
        		.log("Unsupported provider")
        		.to("direct:unsupportedProviderRoute")
        .end();
        
        //Valid provider route to http provider endpoint on localhost:9443
        from("direct:providerARoute")
        .setHeader("Content-Type", constant("application/json"))
        .setHeader("Accept", constant("application/json"))
        .setBody().simple("${body}")
        .marshal().json(JsonLibrary.Jackson, SigninCredentials.class)
        .to("http4://localhost:9443/signin?bridgeEndpoint=true&amp;throwExceptionOnFailure=false&httpClient.SocketTimeout=1000")
        .unmarshal().json(JsonLibrary.Jackson, SigninResponse.class);
        
        //Invalid provider route to http provider endpoint on localhost:944 to simulate connection timeout exception
        from("direct:providerBRoute")
        .setHeader("Content-Type", constant("application/json"))
        .setHeader("Accept", constant("application/json"))
        .setBody().simple("${body}")
        .marshal().json(JsonLibrary.Jackson, SigninCredentials.class)
        .to("http4://localhost:944/signin?bridgeEndpoint=true&amp;throwExceptionOnFailure=false&httpClient.SocketTimeout=1000")
        .unmarshal().json(JsonLibrary.Jackson, SigninResponse.class);
        
      //Invalid provider route to http provider endpoint on localhos:944 to simulate unknown host exception
        from("direct:providerCRoute")
        .setHeader("Content-Type", constant("application/json"))
        .setHeader("Accept", constant("application/json"))
        .setBody().simple("${body}")
        .marshal().json(JsonLibrary.Jackson, SigninCredentials.class)
        .to("http4://localhos:944/signin?bridgeEndpoint=true&amp;throwExceptionOnFailure=false&httpClient.SocketTimeout=1000")
        .unmarshal().json(JsonLibrary.Jackson, SigninResponse.class);
        
        //Unsupported provider route
        from("direct:unsupportedProviderRoute")
        .process(new Processor() {
    		public void process(Exchange exchange) {
            	
                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                
                String msg = "Unsupported provider";
                ErrorResponse response = new ErrorResponse("NOTFOUND", msg, Arrays.asList(msg));
                exchange.getOut().setBody(response);
            }
    	})
    	.marshal().json(JsonLibrary.Jackson, ErrorResponse.class);
        
        
        //Mock consumer http providerA endpoint
        from("jetty:http://localhost:9443/signin?httpMethodRestrict=POST")
        .unmarshal().json(JsonLibrary.Jackson, SigninCredentials.class)
        .to("direct:providerAcbr");
        
        from("direct:providerAcbr")
        .choice()
        	.when().simple("${body.email} == 'isha@sc.in' && ${body.passwd} == 'isha123'")
        	  	.to("direct:signinResponse")
        	.when().simple("${body.email} == 'delayed@sc.in' && ${body.passwd} == 'delayed123'")
        	    .to("direct:delayedSigninResponse")
        	.otherwise()
        		.to("direct:errorResponse")
        .end();
        	
    	from("direct:signinResponse")
    	.process(new Processor() {
            public void process(Exchange exchange) {
            	
                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                
                SigninResponse response = new SigninResponse("qwerty");
                exchange.getOut().setBody(response);
            }
        })
    	.marshal().json(JsonLibrary.Jackson, SigninResponse.class);
    	
    	from("direct:delayedSigninResponse")
    	.delay(60000) //To simulate socket read timeout exception
    	.process(new Processor() {
            public void process(Exchange exchange) {
            	
                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                
                SigninResponse response = new SigninResponse("asdfgh");
                exchange.getOut().setBody(response);
            }
        })
    	.marshal().json(JsonLibrary.Jackson, SigninResponse.class);
    	
    	from("direct:errorResponse")
    	.process(new Processor() {
    		public void process(Exchange exchange) {
            	
                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 401);
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                
                String msg = "Email or password is incorrect";
                ErrorResponse response = new ErrorResponse("UNAUTHORIZED", msg, Arrays.asList(msg));
                exchange.getOut().setBody(response);
            }
    	})
    	.marshal().json(JsonLibrary.Jackson, ErrorResponse.class);
        
        //Mock http producer endpoint - success response from providerA
    	from("timer:mockHttpProducer?repeatCount=1")
    	.log("Calling mock http producer endpoint to http provider A - success response from providerA")
    	.process(new Processor() {
    		public void process(Exchange exchange) {
    			exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    			
    			SigninCredentials credentials = new SigninCredentials("isha@sc.in", "isha123", "providerA");
    			exchange.getIn().setBody(credentials);
    		}
    	})
    	.marshal().json(JsonLibrary.Jackson, SigninCredentials.class)
    	.to("http4://localhost:8080/router/signin")
    	.convertBodyTo(String.class)
    	.to("log:INFO?showBody=true&showHeaders=true");
    	
    	//Mock http producer endpoint - delayed success response from providerA - socket read timeout
    	from("timer:mockHttpProducer?repeatCount=1")
    	.log("Calling mock http producer endpoint to http provider A - delayed response from providerA - socket read timeout")
    	.process(new Processor() {
    		public void process(Exchange exchange) {
    			exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    			
    			SigninCredentials credentials = new SigninCredentials("delayed@sc.in", "delayed123", "providerA");
    			exchange.getIn().setBody(credentials);
    		}
    	})
    	.marshal().json(JsonLibrary.Jackson, SigninCredentials.class)
    	.to("http4://localhost:8080/router/signin?throwExceptionOnFailure=false")
    	.convertBodyTo(String.class)
    	.to("log:INFO?showBody=true&showHeaders=true");
    	
    	//Mock http producer endpoint - error response from providerA
    	from("timer:mockHttpProducer?repeatCount=1")
    	.log("Calling mock http producer endpoint to http provider A - error response from providerA")
    	.process(new Processor() {
    		public void process(Exchange exchange) {
    			exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    			
    			SigninCredentials credentials = new SigninCredentials("isha@sc.in", "isha12", "providerA");
    			exchange.getIn().setBody(credentials);
    		}
    	})
    	.marshal().json(JsonLibrary.Jackson, SigninCredentials.class)
    	.to("http4://localhost:8080/router/signin?throwExceptionOnFailure=false")
    	.convertBodyTo(String.class)
    	.to("log:INFO?showBody=true&showHeaders=true");
    	
    	//Mock http producer endpoint to http provider B - connection timeout
    	from("timer:mockHttpProducer?repeatCount=1")
    	.log("Calling mock http producer endpoint to http provider B - connection timeout")
    	.process(new Processor() {
    		public void process(Exchange exchange) {
    			exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    			
    			SigninCredentials credentials = new SigninCredentials("isha@sc.in", "isha123", "providerB");
    			exchange.getIn().setBody(credentials);
    		}
    	})
    	.marshal().json(JsonLibrary.Jackson, SigninCredentials.class)
    	.to("http4://localhost:8080/router/signin?throwExceptionOnFailure=false")
    	.convertBodyTo(String.class)
    	.to("log:INFO?showBody=true&showHeaders=true");
    	
    	//Mock http producer endpoint to http provider C - unknown host exception
    	from("timer:mockHttpProducer?repeatCount=1")
    	.log("Calling mock http producer endpoint to http provider C - unknown host")
    	.process(new Processor() {
    		public void process(Exchange exchange) {
    			exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    			
    			SigninCredentials credentials = new SigninCredentials("isha@sc.in", "isha123", "providerC");
    			exchange.getIn().setBody(credentials);
    		}
    	})
    	.marshal().json(JsonLibrary.Jackson, SigninCredentials.class)
    	.to("http4://localhost:8080/router/signin?throwExceptionOnFailure=false")
    	.convertBodyTo(String.class)
    	.to("log:INFO?showBody=true&showHeaders=true");
    	
    	//Mock http producer endpoint to http provider D - unsupported provider
    	from("timer:mockHttpProducer?repeatCount=1")
    	.log("Calling mock http producer endpoint to http provider C - unsupported provider")
    	.process(new Processor() {
    		public void process(Exchange exchange) {
    			exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    			
    			SigninCredentials credentials = new SigninCredentials("isha@sc.in", "isha123", "providerD");
    			exchange.getIn().setBody(credentials);
    		}
    	})
    	.marshal().json(JsonLibrary.Jackson, SigninCredentials.class)
    	.to("http4://localhost:8080/router/signin?throwExceptionOnFailure=false")
    	.convertBodyTo(String.class)
    	.to("log:INFO?showBody=true&showHeaders=true");
    }
}
