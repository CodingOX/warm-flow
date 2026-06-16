package org.dromara.warm.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * demo 应用入口，只承载本地联调壳和静态工作台页面。
 */
@SpringBootApplication
public class WarmFlowDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(WarmFlowDemoApplication.class, args);
    }
}
