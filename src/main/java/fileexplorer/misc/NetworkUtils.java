package fileexplorer.misc;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

public class NetworkUtils {

    public static boolean isValidEmailAddress(String emailAddress) {
        String regexPattern = "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@"
                + "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
        return Pattern.compile(regexPattern)
                .matcher(emailAddress)
                .matches();
    }

    public String findIP(HttpServletRequest req) {
        String[] IP_HEADERS = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };
        String clientIP = null;
        for (String header : IP_HEADERS) {
            String v = req.getHeader(header);
            if (v == null || v.isEmpty()) {
                continue;
            }
            String[] parts = v.split("\\s*,\\s*");
            clientIP = parts[0];
            break;
        }
        if (clientIP == null) {
            clientIP = req.getRemoteAddr();
        }
        return clientIP;
    }
}
