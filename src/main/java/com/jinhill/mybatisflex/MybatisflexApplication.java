package com.jinhill.mybatisflex;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.jinhill.mybatisflex.domain.mapper")
public class MybatisflexApplication {

	public static void main(String[] args) {
		SpringApplication.run(MybatisflexApplication.class, args);
	}

}
