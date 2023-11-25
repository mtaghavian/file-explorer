package fileexplorer.misc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;

import java.util.Optional;

public class Utils {

    public static String firstCapitalLetter(String s) {
        return ("" + s.charAt(0)).toUpperCase() + s.substring(1);
    }

    public static Object fromJsonNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (JsonNodeType.BOOLEAN.equals(node.getNodeType())) {
            return node.asBoolean();
        } else if (JsonNodeType.STRING.equals(node.getNodeType())) {
            return node.asText();
        } else if (JsonNodeType.NUMBER.equals(node.getNodeType())) {
            return node.asLong();
        }
        return node.asText();
    }

    public static <T> T get(Optional<T> a) {
        if (a.isPresent()) {
            return a.get();
        } else {
            return null;
        }
    }

}
