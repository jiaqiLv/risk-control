package com.risk.txn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Transaction Service Application
 *
 * 交易服务主应用类，负责：
 * - 交易数据存储与查询
 * - 用户历史统计
 * - 实时交易特征计算
 *
 * @author Risk Control Team
 * @version 1.0.0
 */
@SpringBootApplication(
    exclude = {
        org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration.class
    }
)
@EnableJpaAuditing
@EnableJpaRepositories(
    basePackages = "com.risk.data.repository",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ".*R2dbcRepository"
    )
)
@EntityScan(basePackages = "com.risk.data.entity")
public class TransactionApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionApplication.class, args);
    }
}
