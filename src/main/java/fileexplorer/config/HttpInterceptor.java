package fileexplorer.config;

import fileexplorer.Application;
import fileexplorer.misc.StreamUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Component
public class HttpInterceptor implements HandlerInterceptor {

    @Autowired
    private ServletContext servletContext;

    public HttpInterceptor() {
    }

    public MediaType getMediaType(String fileName) {
        try {
            String mimeType = servletContext.getMimeType(fileName);
            MediaType mediaType = MediaType.parseMediaType(mimeType);
            return mediaType;
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if ("".equals(uri) || "/".equals(uri)) {
            uri = "/index.html";
        }

        if (!uri.startsWith("/api/")) {
            returnFile(request, response, uri);
            return false;
        }
        if (!replyImmediately(uri, request, response)) {
            return false;
        }

        return true;
    }

    private boolean replyImmediately(String uri, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (uri.equals("/api/clearCache")) {
            StreamUtils.responseOnHtml(response, "Done!", true);
            response.setHeader("Clear-Site-Data", "\"cache\"");
            response.setStatus(HttpServletResponse.SC_OK);
            return false;
        }
        return true;
    }

    private void returnFile(HttpServletRequest request, HttpServletResponse response, String uri) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET,POST,DELETE,PUT,OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Access-Control-Allow-Credentials", "" + true);
        response.setHeader("Access-Control-Max-Age", Application.appConfig.getProperty("max-age"));
        response.setHeader("Cache-Control", "max-age=" + Application.appConfig.getProperty("max-age"));

        File file = new File(Application.resPath + uri);
        if (!uri.contains("/../") && file.exists()) {
            response.setContentType("" + getMediaType(uri.substring(1)));
            StreamUtils.copy(new FileInputStream(file), response.getOutputStream(), true, false);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        StreamUtils.responseOnHtml(response, "<div class=\"Centered\" style=\"text-align: center;\"><img src=\"notfound.png\" style=\"width: 50%\"></div>\n\n" + "Page not found! Page=" + uri, true);
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) throws Exception {
    }
}
