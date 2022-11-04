
package com.github.bartebor.dgs.webflux.fileupload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor;
import com.netflix.graphql.dgs.webflux.handlers.DgsWebfluxHttpHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Replaces default DGS webflux hattp handler with our implementation in order to support file upload.
 */
@Configuration
class WebFluxGraphqlConfiguration {
    @Bean
    DgsWebfluxHttpHandler dgsWebfluxHttpHandler(
            DgsReactiveQueryExecutor dgsQueryExecutor,
            @Qualifier("dgsObjectMapper") ObjectMapper dgsObjectMapper) {
        return new MultipartDgsWebFluxHttpHandler(dgsQueryExecutor, dgsObjectMapper);
    }
}
