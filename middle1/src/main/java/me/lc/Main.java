package me.lc;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@EnableAutoConfiguration
@RestController
@ComponentScan(basePackages = "me.lc")
public class Main {

    public static MiddleProcessor middleProcessor;

    public static void main(String[] args) {
        middleProcessor=new MiddleProcessor();

        String port = System.getProperty("server.port");
        SpringApplication.run(Main.class, "--server.port=" + port);
    }

    @RequestMapping("/ready")
    public String ready() throws InterruptedException {
        while(!MiddleProcessor.MIDDLE_PROCESSOR_READY){
            Thread.sleep(10);
        }
        return "suc";
    }

    @RequestMapping("/setParameter")
    public String setParameter(@RequestParam Integer port) {
        Constants.DATA_SOURCE_PORT = String.valueOf(port);
        middleProcessor.start();
        return "suc";
    }
}
