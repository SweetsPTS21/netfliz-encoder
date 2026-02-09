package com.netfliz.encoder.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netfliz.encoder.constant.StringPools;
import jakarta.validation.ValidationException;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

@Component
public class JsonUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static JsonNode parse(String jsonString) {
        if (Strings.isBlank(jsonString)) {
            throw new ValidationException("Json parse error: Config is empty");
        }

        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            throw new ValidationException("Json parse error: Config is invalid");
        }
    }

    public static JsonNode parse(List<String> listString) {
        try {
            if (CollectionUtils.isEmpty(listString)) {
                return null;
            }
            return objectMapper.valueToTree(listString);
        } catch (Exception e) {
            throw new ValidationException("Json parse error: Config is invalid");
        }
    }

    public static JsonNode parse(Object object) {
        try {
            return objectMapper.valueToTree(object);
        } catch (Exception e) {
            throw new ValidationException("Json parse error: Config is invalid");
        }
    }

    public static <T> T parse(JsonNode jsonNode, Class<T> clazz) {
        try {
            return objectMapper.treeToValue(jsonNode, clazz);
        } catch (Exception e) {
            throw new ValidationException("Json parse error: Config is invalid");
        }
    }

    public static String serialize(JsonNode jsonNode) {
        if (Objects.isNull(jsonNode)) {
            return StringPools.BLANK;
        }

        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception e) {
            throw new ValidationException("Json serialize error: Config is invalid");
        }
    }

    public static String serialize(Object object) {
        try {
            if (Objects.isNull(object)) {
                return StringPools.BLANK;
            }
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new ValidationException("Json serialize error: Config is invalid");
        }
    }

    public static <T> List<T> parseList(String jsonString, Class<T> clazz) {
        try {
            if (Strings.isBlank(jsonString)) {
                return null;
            }
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            throw new ValidationException("Json parse error: Config is invalid");
        }
    }

    public static boolean validJson(String jsonString) {
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
