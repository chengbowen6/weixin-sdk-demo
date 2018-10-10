package com.riversoft.weixin.demo.qydev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Created by exizhai on 10/7/2015.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.riversoft.weixin.demo")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}