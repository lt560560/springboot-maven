package cn.zhangxd.trip.client.mobile.security;

import cn.zhangxd.trip.client.mobile.security.exception.CustomWebResponseExceptionTranslator;
import cn.zhangxd.trip.service.api.service.ITripUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.error.OAuth2AccessDeniedHandler;
import org.springframework.security.oauth2.provider.error.OAuth2AuthenticationEntryPoint;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

/**
 * OAuth2.0 Server配置
 * Created by zhangxd on 16/3/17.
 * <p>
 * 1.请求token
 * curl -X POST -vu api:12345 http://localhost:8081/oauth/token -H "Accept: application/json" -d "password=spring&username=roy&grant_type=password&scope=read%20write"
 * 2.使用token请求
 * curl -i -H "Authorization: Bearer 105e56d2-f29b-4dec-bcf5-15dde21e8a95" http://localhost:8081/hello?name=123
 * 3.刷新token
 * curl -X POST -vu api:12345 http://localhost:8081/oauth/token -H "Accept: application/json" -d "grant_type=refresh_token&refresh_token=18198e43-64bd-40b3-883b-6b88a21ea21c"
 * 4.登出
 * curl -i -H "Authorization: Bearer 7ccda303-ac2f-4c6d-b249-7d1185d6c1ab" http://localhost:8081/oauth/logout
 * </p>
 */
@Configuration
public class OAuth2ServerConfig {

    private static final String RESOURCE_ID_API = "trip_resource";

    private static final String ROLE_USER = "USER";

    @Configuration
    @EnableResourceServer
    protected static class ApiResourceServerConfig extends ResourceServerConfigurerAdapter {

        @Autowired
        private CustomLogoutSuccessHandler customLogoutSuccessHandler;

        @Bean
        public OAuth2AuthenticationEntryPoint resourceAuthenticationEntryPoint() {
            OAuth2AuthenticationEntryPoint oae = new OAuth2AuthenticationEntryPoint();
            oae.setExceptionTranslator(new CustomWebResponseExceptionTranslator());
            return oae;
        }

        @Bean
        public OAuth2AccessDeniedHandler resourceAccessDeniedHandler() {
            OAuth2AccessDeniedHandler oad = new OAuth2AccessDeniedHandler();
            oad.setExceptionTranslator(new CustomWebResponseExceptionTranslator());
            return oad;
        }

        @Override
        public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
            resources
                    .resourceId(RESOURCE_ID_API)
                    .authenticationEntryPoint(this.resourceAuthenticationEntryPoint())
                    .accessDeniedHandler(this.resourceAccessDeniedHandler())
            ;
        }

        @Override
        public void configure(HttpSecurity http) throws Exception {
            http
                    .csrf().disable()
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                    .authorizeRequests()
                    .anyRequest().authenticated()
                    .and()
                    .logout()
                    .logoutUrl("/oauth/logout")
                    .logoutSuccessHandler(customLogoutSuccessHandler)
            ;
        }
    }

    @Configuration
    @EnableAuthorizationServer
    @EnableConfigurationProperties(AuthorizationProperties.class)
    protected static class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {

        @Autowired
        @Qualifier("authenticationManagerBean")
        private AuthenticationManager authenticationManager;

        @Autowired
        private RedisConnectionFactory redisConnectionFactory;

        @Autowired
        private AuthorizationProperties authorizationProperties;

        @Bean
        public TokenStore tokenStore() {
            return new RedisTokenStore(this.redisConnectionFactory);
        }

        @Override
        public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
            // 定义了客户端细节服务
            clients
                    .inMemory()
                    .withClient(authorizationProperties.getClientId())
                    .secret(authorizationProperties.getClientSecret())
                    .authorizedGrantTypes(authorizationProperties.getAuthorizedGrantTypes())
                    .authorities(ROLE_USER)
                    .scopes(authorizationProperties.getScope())
                    .resourceIds(RESOURCE_ID_API)
                    .accessTokenValiditySeconds(authorizationProperties.getAccessTokenValiditySeconds())
                    .refreshTokenValiditySeconds(authorizationProperties.getRefreshTokenValiditySeconds())
            ;
        }

        @Bean
        public OAuth2AuthenticationEntryPoint serverAuthenticationEntryPoint() {
            OAuth2AuthenticationEntryPoint oae = new OAuth2AuthenticationEntryPoint();
            oae.setExceptionTranslator(new CustomWebResponseExceptionTranslator());
            return oae;
        }

        @Bean
        public OAuth2AccessDeniedHandler serverAccessDeniedHandler() {
            OAuth2AccessDeniedHandler oad = new OAuth2AccessDeniedHandler();
            oad.setExceptionTranslator(new CustomWebResponseExceptionTranslator());
            return oad;
        }

        @Override
        public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
            security
                    .authenticationEntryPoint(this.serverAuthenticationEntryPoint())
                    .accessDeniedHandler(this.serverAccessDeniedHandler())
            ;
        }

        @Autowired
        private ITripUserService tripUserService;

        @Override
        public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
            // 定义了授权和令牌端点和令牌服务
            endpoints
                    .tokenStore(this.tokenStore())
                    .authenticationManager(this.authenticationManager)
                    .userDetailsService(this.tripUserService)
                    .allowedTokenEndpointRequestMethods(HttpMethod.POST)
                    .exceptionTranslator(new CustomWebResponseExceptionTranslator())
            ;
        }

        @Bean
        @Primary
        public DefaultTokenServices tokenServices() {
            final DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
            defaultTokenServices.setTokenStore(this.tokenStore());
            defaultTokenServices.setSupportRefreshToken(true);
            return defaultTokenServices;
        }
    }

}
