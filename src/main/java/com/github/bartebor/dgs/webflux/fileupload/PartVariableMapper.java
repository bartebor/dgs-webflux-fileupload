package com.github.bartebor.dgs.webflux.fileupload;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.codec.multipart.Part;

/**
 * Inserts file objects into variables according to supplied mapping.
 * This code is based on MultipartVariableMapper, but injects Part objects instead of MultipartFile.
 */
public class PartVariableMapper {
    private Pattern PERIOD = Pattern.compile("\\.");

    private interface Mapper<T> {
        Object set(T location, String target, Part value);
        Object recurse(T location, String target);
    }

    private Mapper<Map<String, Object>> MAP_MAPPER = new Mapper<Map<String, Object>>() {
        @Override
        public Object set(Map<String, Object> location, String target, Part value) {
            return location.put(target, value);
        }

        @Override
        public Object recurse(Map<String, Object> location, String target) {
            return Optional.ofNullable(location.get(target))
                    .orElseThrow(IllegalArgumentException::new);
        }
    };

    private Mapper<List<Object>> LIST_MAPPER = new Mapper<List<Object>>() {
        @Override
        public Object set(List<Object> location, String target, Part value) {
            return location.set(Integer.parseInt(target), value);
        }

        @Override
        public Object recurse(List<Object> location, String target) {
            return location.get(Integer.parseInt(target));
        }
    };

    void mapVariable(String objectPath, Map<String, Object> variables, Part part) {
        final String[] segments = PERIOD.split(objectPath);

        if (segments.length < 2) {
            throw new RuntimeException("object-path in map must have at least two segments");
        } else if (!"variables".equals(segments[0])) {
            throw new RuntimeException("can only map into variables");
        }

        Object currentLocation = variables;
        for (int i = 1; i < segments.length; ++i) {
            final String segmentName = segments[i];
            if (i == segments.length - 1) {
                if (currentLocation instanceof Map) {
                    if (null != MAP_MAPPER.set((Map<String, Object>)currentLocation, segmentName, part)) {
                        throw new RuntimeException("expected null value when mapping " + objectPath);
                    }
                } else {
                    if (null != LIST_MAPPER.set((List<Object>)currentLocation, segmentName, part)) {
                        throw new RuntimeException("expected null value when mapping " + objectPath);
                    }
                }
            } else {
                if (currentLocation instanceof Map) {
                    currentLocation = MAP_MAPPER.recurse((Map<String, Object>)currentLocation, segmentName);
                } else {
                    currentLocation = LIST_MAPPER.recurse((List<Object>)currentLocation, segmentName);
                }
                if (null == currentLocation) {
                    throw new RuntimeException("found null intermediate value when trying to map " + objectPath);
                }
            }
        }
    }
}
