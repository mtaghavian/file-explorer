package fileexplorer.misc;

import fileexplorer.Application;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketSession;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class StreamUtils {

    private static DecimalFormat DEC_FORMAT = new DecimalFormat("#.##");
    private static long BYTE = 1L;
    private static long KB = BYTE * 1000;
    private static long MB = KB * 1000;
    private static long GB = MB * 1000;
    private static long TB = GB * 1000;
    private static long PB = TB * 1000;
    private static long EB = PB * 1000;

    public static void responseOnHtml(HttpServletResponse response, String msg, boolean center) throws IOException {
        String page = readString(new File(Application.resPath + "/response.html"));
        if (center) {
            page = page.replace("Here-goes-the-response", "<pre style=\"border: none; text-align: center;\"><code>" + msg + "</code></pre>");
        } else {
            page = page.replace("Here-goes-the-response", "<pre style=\"border: none;\"><code>" + msg + "</code></pre>");
        }
        response.setContentType("text/html");
        response.getOutputStream().write(page.getBytes());
        response.setStatus(HttpServletResponse.SC_OK);
    }

    public static void writeString(String str, File file) throws IOException {
        writeBytes(str.getBytes("UTF-8"), file);
    }

    public static void writeBytes(byte b[], File file) throws IOException {
        OutputStream os = new FileOutputStream(file);
        os.write(b);
        os.flush();
        os.close();
    }

    public static String readString(File file) throws IOException {
        return new String(readBytes(file), "UTF-8");
    }

    public static String readString(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        copy(is, os, false, false);
        return new String(os.toByteArray(), "UTF-8");
    }

    public static void listFiles(File file, List<File> list) {
        if (file.isDirectory()) {
            list.add(file);
            for (File f : file.listFiles()) {
                listFiles(f, list);
            }
        } else {
            list.add(file);
        }
    }

    public static byte[] readBytes(String filename, long start, long end) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            return null;
        }
        FileInputStream fis = new FileInputStream(file);
        fis.skip(start);
        byte[] result = new byte[(int) (end - start) + 1];
        DataInputStream is = new DataInputStream(fis);
        is.readFully(result);
        is.close();
        fis.close();
        return result;
    }

    public static byte[] readBytes(File file) throws IOException {
        if (!file.exists()) {
            return null;
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        copy(new FileInputStream(file), os, true, true);
        byte b[] = os.toByteArray();
        return b;
    }

    public static void copy(InputStream is, OutputStream os, boolean closeInput, boolean closeOutput) throws IOException {
        byte b[] = new byte[4000];
        while (true) {
            int r = is.read(b);
            if (r < 0) {
                break;
            }
            os.write(b, 0, r);
        }
        if (closeInput) {
            is.close();
        }
        if (closeOutput) {
            os.flush();
            os.close();
        }
    }

    public static String hash(String s) {
        try {
            if (s == null) {
                s = "";
            }
            return hash(s.getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String hash(byte b[]) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(b);
            return toHex(hash, "");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    public static String encrypt(String text, String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(digest.digest(key.getBytes("UTF-8")), "AES"),
                new IvParameterSpec(new byte[16]));
        byte[] encrypted = cipher.doFinal(text.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String toBase64(byte b[]) {
        return Base64.getEncoder().encodeToString(b);
    }

    public static File createTempFile(String ext) throws IOException {
        return File.createTempFile("" + Application.appName, ext, new File(Application.tmpPath + "/"));
    }

    public static String decrypt(String text, String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(digest.digest(key.getBytes("UTF-8")), "AES"),
                new IvParameterSpec(new byte[16]));
        byte[] original = cipher.doFinal(Base64.getDecoder().decode(text));
        return new String(original, "UTF-8");
    }

    private static String formatSize(long size, long divider, String unitName) {
        return DEC_FORMAT.format((double) size / divider) + " " + unitName;
    }

    public static String toHumanReadableSIPrefixes(long size) {
        if (size < 0)
            throw new IllegalArgumentException("Invalid file size: " + size);
        if (size >= EB) return formatSize(size, EB, "EB");
        if (size >= PB) return formatSize(size, PB, "PB");
        if (size >= TB) return formatSize(size, TB, "TB");
        if (size >= GB) return formatSize(size, GB, "GB");
        if (size >= MB) return formatSize(size, MB, "MB");
        if (size >= KB) return formatSize(size, KB, "KB");
        return formatSize(size, BYTE, "Bytes");
    }

    public static String toHex(byte b[], String delimeter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            String h = String.format("%h", b[i] & 0xff);
            h = (h.length() == 1) ? "0" + h : h;
            sb.append((i == 0) ? h : (delimeter + h));
        }
        return sb.toString();
    }

    public static byte[] fromHex(String hex, String delimeter) {
        int step = 2 + delimeter.length();
        byte b[] = new byte[(int) Math.ceil((double) hex.length() / step)];
        for (int i = 0; i < hex.length(); i += step) {
            String h = hex.substring(i, i + 2);
            b[i / step] = (byte) Integer.parseInt(h, 16);
        }
        return b;
    }

    public static void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            for (File inner : file.listFiles()) {
                delete(inner);
            }
        }
        file.delete();
    }

    public static String toString(Exception ex) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));
            ex.printStackTrace(writer);
            writer.close();
            return new String(os.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public static String getCurrentTime() {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        format.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        return format.format(new Date());
    }

    public static int getFreeMemPercent() {
        return (int) ((double) Runtime.getRuntime().freeMemory() / Runtime.getRuntime().totalMemory() * 100.0);
    }

    public static Map<String, String> readCookies(WebSocketSession wss) {
        Map<String, String> map = new TreeMap<>();
        List<String> cookie = wss.getHandshakeHeaders().get("cookie");
        for (String c : cookie) {
            String[] s = c.split(";");
            for (String e : s) {
                String[] r = e.trim().split("=");
                map.put(r[0], r[1]);
            }
        }
        return map;
    }

    public static Map<String, String> readCookies(HttpServletRequest request) {
        Map<String, String> map = new TreeMap<>();
        Cookie cookie[] = request.getCookies();
        if (cookie == null) {
            return map;
        }
        for (Cookie c : cookie) {
            map.put(c.getName(), c.getValue());
        }
        return map;
    }

    public static String getCookie(String key, Map<String, String> cookies) {
        try {
            key = StreamUtils.toHex(key.getBytes(), "");
            if (cookies.containsKey(key)) {
                return new String(StreamUtils.fromHex("" + cookies.get(key), ""));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static String getCookie(String key, WebSocketSession wss) {
        return getCookie(key, readCookies(wss));
    }

    public static String getCookie(String key, HttpServletRequest req) {
        return getCookie(key, readCookies(req));
    }

    public static byte[] fromBase64(String s) {
        return Base64.getDecoder().decode(s);
    }

    public static void reverse(byte[] b) {
        for (int i = 0; i < b.length / 2; i++) {
            byte t = b[i];
            b[i] = b[b.length - 1 - i];
            b[b.length - 1 - i] = t;
        }
    }

    public static byte[] zip(byte[] b) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream zos = new GZIPOutputStream(baos);
        zos.write(b);
        zos.close();
        return baos.toByteArray();
    }

    public static byte[] unzip(byte[] b) throws IOException {
        GZIPInputStream zis = new GZIPInputStream(new ByteArrayInputStream(b));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(zis, baos, true, true);
        return baos.toByteArray();
    }

    public static String getUserAddressExtended(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        String separator = ": ";
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
        for (String header : IP_HEADERS) {
            String v = req.getHeader(header);
            if (v == null || v.isEmpty()) {
                continue;
            }
            sb.append(header + separator + v).append(", ");
        }
        sb.append("REMOTE_ADDR" + separator + req.getRemoteAddr());
        return sb.toString();
    }

    public static String getUserAddressDesc(HttpServletRequest req) throws Exception {
        String url = "http://ipwho.is/" + req.getRemoteAddr();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity entity = new HttpEntity(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }
}
