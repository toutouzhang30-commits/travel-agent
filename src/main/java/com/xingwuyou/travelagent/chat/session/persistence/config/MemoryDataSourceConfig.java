package com.xingwuyou.travelagent.chat.session.persistence.config;


import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.Map;


//这个是干什么的？
@Configuration
@EnableJpaRepositories(
        basePackages = "com.xingwuyou.travelagent.chat.session.persistence.repository",
        entityManagerFactoryRef = "memoryEntityManagerFactory",
        transactionManagerRef = "memoryTransactionManager"
)
public class MemoryDataSourceConfig {
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties ragDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource ragDataSource(
            @Qualifier("ragDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @ConfigurationProperties("travel.memory.datasource")
    public DataSourceProperties memoryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource memoryDataSource(
            @Qualifier("memoryDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean memoryEntityManagerFactory(
            @Qualifier("memoryDataSource") DataSource dataSource
    ) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.xingwuyou.travelagent.chat.session.persistence.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.dialect", "org.hibernate.dialect.MySQLDialect"
        ));
        return factory;
    }

    @Bean
    public JpaTransactionManager memoryTransactionManager(
            @Qualifier("memoryEntityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
