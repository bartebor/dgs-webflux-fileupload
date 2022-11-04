
package com.github.bartebor.dgs.webflux.fileupload;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.netflix.graphql.dgs.internal.utils.TimeTracer;
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor;
import com.netflix.graphql.dgs.webflux.handlers.DefaultDgsWebfluxHttpHandler;
import com.netflix.graphql.dgs.webflux.handlers.DgsWebfluxHttpHandler;
import graphql.execution.reactive.SubscriptionPublisher;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * HTTP handler with support of file uploads.
 * Normal queries are processed by original DgsWebfluxHttpHandler.
 * Code is based on DgsRestController (MVC).
 */
@Slf4j
public class MultipartDgsWebFluxHttpHandler implements DgsWebfluxHttpHandler {
    final DgsReactiveQueryExecutor dgsQueryExecutor;
    final ObjectMapper dgsObjectMapper;
    final DgsWebfluxHttpHandler defaultDgsWebfluxHttpHandler;
    final static PartVariableMapper PART_VARIABLE_MAPPER = new PartVariableMapper();

    public MultipartDgsWebFluxHttpHandler(DgsReactiveQueryExecutor dgsQueryExecutor, ObjectMapper dgsObjectMapper) {
        this.dgsQueryExecutor = dgsQueryExecutor;
        this.dgsObjectMapper = dgsObjectMapper;
        this.defaultDgsWebfluxHttpHandler = new DefaultDgsWebfluxHttpHandler(dgsQueryExecutor, dgsObjectMapper);
    }


    @Override
    public Mono<ServerResponse> graphql(ServerRequest sr) {
        return sr.multipartData()
                .filter(multipart -> multipart.containsKey("operations") && multipart.containsKey("map"))
                .flatMap(multipart -> Mono.zip(
                        mergeDataBuffers(Optional.ofNullable(multipart.getFirst("operations"))
                                .map(o -> o.content())
                                .orElse(Flux.empty())),
                        mergeDataBuffers(Optional.ofNullable(multipart.getFirst("map"))
                                .map(o -> o.content())
                                .orElse(Flux.empty())),
                        Mono.just(multipart))
                )
                .flatMap(tuple -> {
                    try {
                        final Map<String, Object> inputQuery = dgsObjectMapper.readValue(tuple.getT1(), new TypeReference<Map<String, Object>>() {});
                        final Map<String, List<String>> fileMapInput = dgsObjectMapper.readValue(tuple.getT2(), new TypeReference<Map<String, List<String>>>() {});
                        final MultiValueMap<String, Part> multiPart = tuple.getT3();

                        return executeMultiPartQuery(inputQuery, fileMapInput, multiPart, sr);
                    } catch (IOException ex) {
                        return Mono.error(ex);
                    }
                })
                .switchIfEmpty(defaultDgsWebfluxHttpHandler.graphql(sr));
    }

    private Mono<ServerResponse> executeMultiPartQuery(Map<String, Object> inputQuery, Map<String, List<String>> fileMapInput, MultiValueMap<String, Part> multiPart, final ServerRequest sr) throws IOException {
        final Map<String, Object> queryVariables = Optional.ofNullable((Map<String, Object>)inputQuery.get("variables")).orElse(Map.of());
        fileMapInput.forEach((fileKey, objectPaths) -> {
            objectPaths.forEach(objectPath -> {
                Optional.ofNullable(multiPart.getFirst(fileKey)).ifPresent(file -> {
                    PART_VARIABLE_MAPPER.mapVariable(objectPath, queryVariables, file);
                });
            });
        });

        final Object opName = inputQuery.get("operationName");
        if (opName != null && !(opName instanceof String)) {
            return ServerResponse.badRequest().bodyValue("Invalid GraphQL request - operationName must be a String");
        }
        final String gqlOperationName = (String) opName;

        final Object query = inputQuery.get("query");
        if (query != null && !(query instanceof String)) {
            return ServerResponse.badRequest().bodyValue("Invalid GraphQL request - query must be a String");
        }

        final String gqlQuery = (String) query;

        final Map<String, Object> extensions = Optional.ofNullable((Map<String, Object>) inputQuery.get("extensions")).orElse(Map.of());

        return TimeTracer.INSTANCE.logTime(
                () -> dgsQueryExecutor.execute(
                        gqlQuery,
                        queryVariables,
                        extensions,
                        sr.headers().asHttpHeaders(),
                        gqlOperationName,
                        sr
                ),
                log,
                "Executed query in {}ms")
                .flatMap(executionResult -> {
                    log.debug("Execution result - Contains data: '{}' - Number of errors: {}",
                            executionResult.isDataPresent(),
                            executionResult.getErrors().size());

                    if (executionResult.isDataPresent() && executionResult.getData() instanceof SubscriptionPublisher) {
                        return ServerResponse.badRequest().bodyValue("Trying to execute subscription on /graphql. Use /subscriptions instead!");
                    }

                    return ServerResponse.ok().bodyValue(executionResult.toSpecification());
                })
                .onErrorResume(ex -> {
                    if (ex instanceof JsonParseException) {
                        return ServerResponse.badRequest()
                                .bodyValue("Invalid query - " + ex.getMessage() + ".");
                    } else if (ex instanceof MismatchedInputException) {
                        return ServerResponse.badRequest()
                                .bodyValue("Invalid query - No content to map to input.");
                    } else {
                        return ServerResponse.badRequest()
                                .bodyValue("Invalid query - " + ex.getMessage() + ".");
                    }
                })
                ;
    }

    private Mono<byte[]> mergeDataBuffers(Flux<DataBuffer> dataBufferFlux) {
        return DataBufferUtils.join(dataBufferFlux)
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                });
    }
}
