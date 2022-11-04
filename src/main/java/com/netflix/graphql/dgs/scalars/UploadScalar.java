package com.netflix.graphql.dgs.scalars;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsRuntimeWiring;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import org.springframework.http.codec.multipart.Part;

/**
 * Replaces DGS's UploadScalar so it operates on reactive Part instead of MultipartFile.
 */
@DgsComponent
public class UploadScalar {

    private final static Coercing<Part, Void> PART_COERCING = new Coercing<Part, Void>() {
        @Override
        public Void serialize(Object dataFetcherResult) throws CoercingSerializeException {
            throw new CoercingSerializeException("Upload is an input-only type");
        }

        @Override
        public Part parseValue(Object input) throws CoercingParseValueException {
            if (input instanceof Part part) {
                return part;
            }

            throw new CoercingParseValueException(
                    "Expected type "
                    + Part.class.getName()
                    + " but was "
                    + input.getClass().getName()
            );
        }

        @Override
        public Part parseLiteral(Object input) throws CoercingParseLiteralException {
            throw new CoercingParseLiteralException(
                    "Must use variables to specify Upload values"
            );
        }
    };

    private final static GraphQLScalarType UPLOAD_SCALAR = GraphQLScalarType.newScalar()
            .name("Upload")
            .description("A custom scalar that represents files")
            .coercing(PART_COERCING)
            .build();

    @DgsRuntimeWiring
    public RuntimeWiring.Builder addScalar(RuntimeWiring.Builder builder) {
        return builder.scalar(UPLOAD_SCALAR);
    }
}
