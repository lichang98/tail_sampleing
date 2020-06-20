package me.lc;

import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.ConnectException;

@EnableAutoConfiguration
@RestController
@ComponentScan(basePackages = "me.lc")
public class Main {
    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class.getName());
    public static BackendProcessor backendProcessor;

    public static void main(String[] args) throws InterruptedException, IOException {
        backendProcessor=new BackendProcessor();
        String port = System.getProperty("server.port");
        SpringApplication.run(Main.class, "--server.port=" + port);
    }

    @RequestMapping("/ready")
    public String ready() throws InterruptedException, IOException {
        while(!BackendProcessor.BACKEND_PROCESSOR_READY){
            Thread.sleep(10);
        }
        return "suc";
    }

    @RequestMapping("/setParameter")
    public String setParameter(@RequestParam Integer port) {
        Constants.DATA_SOURCE_PORT = String.valueOf(port);
        backendProcessor.start();
        return "suc";
    }
}
