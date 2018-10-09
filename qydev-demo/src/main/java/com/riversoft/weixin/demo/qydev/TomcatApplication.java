package com.riversoft.weixin.demo.qydev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ServletComponentScan
@ComponentScan(basePackages = "com.riversoft.weixin.demo")
public class TomcatApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(TomcatApplication.class, args);
    }
	@Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder)
    {		// 注意这里要指向原先用main方法执行的Application启动类
         	return builder.sources(TomcatApplication.class);
    }
}
