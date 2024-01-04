package fileexplorer;

import fileexplorer.config.HttpInterceptor;
import fileexplorer.config.WebRTCController;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

@SpringBootApplication(exclude = ErrorMvcAutoConfiguration.class)
@EnableScheduling
@EnableWebSocket
public class Application implements ApplicationContextAware {

    public static final String appName = "File-Explorer",
            configFile = "./application.properties",
            resPath = "./res",
            tmpPath = "./tmp",
            publicPath = "./public";
    public static final Properties appConfig = new Properties();
    private static ConfigurableApplicationContext context;

    public static void main(String[] args) throws IOException {
        FileInputStream is = new FileInputStream(Application.configFile);
        appConfig.load(is);
        is.close();

        new File(tmpPath).mkdir();
        new File(resPath).mkdir();
        new File(publicPath).mkdir();

        context = SpringApplication.run(Application.class, args);
    }

    public static ApplicationContext getContext() {
        return context;
    }

    public static void restart() {
        Thread thread = new Thread(() -> {
            ApplicationArguments args = context.getBean(ApplicationArguments.class);
            context.close();
            context = SpringApplication.run(Application.class, args.getSourceArgs());
        });
        thread.setDaemon(false);
        thread.start();
    }

    public static void shutdown() {
        new Thread(() -> {
            context.close();
            System.exit(0);
        }).start();
    }

    public static <T> T getBean(Class<T> requiredType) {
        return context.getBean(requiredType);
    }

    @Override
    public void setApplicationContext(ApplicationContext ac) throws BeansException {
        context = (ConfigurableApplicationContext) ac;
    }

    @Bean(name = "multipartResolver")
    public CommonsMultipartResolver multipartResolver() throws IOException {
        CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver();
        multipartResolver.setMaxUploadSize(10000000000l);
        multipartResolver.setUploadTempDir(new FileSystemResource(tmpPath));
        multipartResolver.setMaxInMemorySize(-1);
        return multipartResolver;
    }

    @Configuration
    public class InterceptorConfig extends WebMvcConfigurerAdapter {

        @Autowired
        private HttpInterceptor serviceInterceptor;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(serviceInterceptor);
        }
    }

    @Configuration
    public class WSConfig implements WebSocketConfigurer {

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(new WebRTCController(), "/webrtc").setAllowedOrigins("*");
        }
    }

}
