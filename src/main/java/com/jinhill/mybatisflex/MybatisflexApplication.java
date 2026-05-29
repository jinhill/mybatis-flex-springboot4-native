package com.jinhill.mybatisflex;

import com.jinhill.mybatisflex.config.MyBatisFlexNativeConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@MapperScan("com.jinhill.mybatisflex.domain.mapper")
@Import(MyBatisFlexNativeConfig.class)
public class MybatisflexApplication {

    public static void main(String[] args) {
        SpringApplication.run(MybatisflexApplication.class, args);
    }

}
