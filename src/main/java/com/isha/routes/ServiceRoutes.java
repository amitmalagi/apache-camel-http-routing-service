package com.isha.routes;

import java.util.Arrays;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
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

        onException(HttpOperationFailedException.class)
        .handled(true)
        .process(new Processor() {
            public void process(Exchange exchange) {
                // copy the caused exception values to the exchange as we want the response in the regular exchange
                // instead as an exception that will get thrown and thus the route breaks
                HttpOperationFailedException cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, HttpOperationFailedException.class);
                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, cause.getStatusCode());
                exchange.getOut().setBody(cause.getResponseBody());
                
                Map<String, String> headersMap = cause.getResponseHeaders();
                for (Map.Entry<String, String> entry : headersMap.entrySet()) {
                    exchange.getOut().setHeader(entry.getKey(), entry.getValue());                    
                }
            }
        });
        
        onException(HttpHostConnectException.class)
        .handled(true)
        .process(new Processor() {
            public void process(Exchange exchange) {
                // copy the caused exception values to the exchange as we want the response in the regular exchange
                // instead as an exception that will get thrown and thus the route breaks
            	HttpHostConnectException cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, HttpHostConnectException.class);
                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 503);
                
                ErrorResponse errorResponse = new ErrorResponse(
                		"SERVICE_UNAVAILABLE", cause.getLocalizedMessage(), Arrays.asList(cause.getMessage()));
                ObjectMapper mapper = new ObjectMapper();
                
                try {
                	exchange.getOut().setBody(mapper.writeValueAsString(errorResponse));
                    exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                } catch (Exception ex) {
                	LOG.error(ex.toString());
                }
            }
        });
        
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
        	.when().simple("${body.vendor} == 'providerA'")
        	.log("Calling providerA signin api")
        	.to("direct:providerARoute")
        .end();
        
        from("direct:providerARoute")
        .setHeader("Content-Type", constant("application/json"))
        .setHeader("Accept", constant("application/json"))
        .setBody().simple("${body}")
        .marshal().json(JsonLibrary.Jackson, SigninCredentials.class)
        .to("http4://localhost:9443/signin?bridgeEndpoint=true&amp;throwExceptionOnFailure=false")
        .unmarshal().json(JsonLibrary.Jackson, SigninResponse.class);
    }
}
