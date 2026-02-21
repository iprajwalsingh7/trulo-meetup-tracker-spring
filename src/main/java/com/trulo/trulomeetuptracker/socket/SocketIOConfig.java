package com.trulo.trulomeetuptracker.socket;

import com.corundumstudio.socketio.AuthorizationListener;
import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import com.trulo.trulomeetuptracker.model.User;
import com.trulo.trulomeetuptracker.repository.UserRepository;
import com.trulo.trulomeetuptracker.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class SocketIOConfig {

    @Value("${server.port:5000}")
    private int port;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Bean
    public SocketIOServer socketIOServer() {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        // Configure Netty-SocketIO to run alongside the Spring Tomcat server.
        // We set the port to 5001 to prevent conflicts with Spring Web (5000),
        // matching the React client's configured REACT_APP_SOCKET_URL port expectation.
        config.setPort(port + 1);
        config.setOrigin("http://localhost:3000,http://127.0.0.1:3000");

        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        config.setSocketConfig(socketConfig);

        config.setAuthorizationListener(new AuthorizationListener() {
            @Override
            public boolean isAuthorized(HandshakeData data) {
                // Extract the authentication token provided by the React Socket.IO client URL
                // query
                String token = data.getSingleUrlParam("token");
                if (token == null && data.getHttpHeaders().get("Authorization") != null) {
                    String auth = data.getHttpHeaders().get("Authorization");
                    if (auth.startsWith("Bearer ")) {
                        token = auth.substring(7);
                    }
                }

                if (token == null || token.isEmpty()) {
                    return false;
                }

                try {
                    String userId = jwtUtil.extractUserId(token);
                    if (userId != null && jwtUtil.validateToken(token)) {
                        Optional<User> user = userRepository.findById(userId);
                        if (user.isPresent()) {
                            // Store userId in urlParams so handler can access it
                            data.getUrlParams().put("userId", java.util.Collections.singletonList(userId));
                            return true;
                        }
                    }
                } catch (Exception e) {
                    return false;
                }
                return false;
            }
        });

        return new SocketIOServer(config);
    }

    @Bean
    public SpringAnnotationScanner springAnnotationScanner(SocketIOServer socketServer) {
        return new SpringAnnotationScanner(socketServer);
    }
}
