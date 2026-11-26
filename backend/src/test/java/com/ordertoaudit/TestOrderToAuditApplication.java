package com.ordertoaudit;

import org.springframework.boot.SpringApplication;

public class TestOrderToAuditApplication {

	public static void main(String[] args) {
		SpringApplication.from(OrderToAuditApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
