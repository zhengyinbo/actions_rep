package com.bo.personalwebsite.config;

import com.bo.personalwebsite.security.AccountRealm;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ShiroConfig {

    @Bean
    public AccountRealm accountRealm() {
        return new AccountRealm();
    }

    @Bean
    public SecurityManager securityManager(AccountRealm accountRealm) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(accountRealm);
        return securityManager;
    }

    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean(SecurityManager securityManager) {
        ShiroFilterFactoryBean factoryBean = new ShiroFilterFactoryBean();
        factoryBean.setSecurityManager(securityManager);

        Map<String, String> filterChain = new LinkedHashMap<>();
        filterChain.put("/api/health", "anon");
        filterChain.put("/api/site/**", "anon");
        filterChain.put("/api/projects", "anon");
        filterChain.put("/api/articles", "anon");
        filterChain.put("/api/auth/login", "anon");
        filterChain.put("/api/auth/logout", "anon");
        filterChain.put("/api/admin/**", "authc");
        filterChain.put("/api/**", "anon");
        factoryBean.setFilterChainDefinitionMap(filterChain);

        return factoryBean;
    }
}

