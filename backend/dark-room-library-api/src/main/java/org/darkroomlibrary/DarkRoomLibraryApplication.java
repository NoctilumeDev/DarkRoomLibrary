package org.darkroomlibrary;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("org.darkroomlibrary.mapper")
@SpringBootApplication
public class DarkRoomLibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DarkRoomLibraryApplication.class, args);
    }
}
