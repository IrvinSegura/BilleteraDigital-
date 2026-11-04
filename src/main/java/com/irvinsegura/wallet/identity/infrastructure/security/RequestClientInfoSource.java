package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.domain.model.ClientInfo;
import com.irvinsegura.wallet.identity.domain.port.ClientInfoSource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class RequestClientInfoSource implements ClientInfoSource {

    @Override
    public ClientInfo current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return ClientInfo.unknown();
        }
        HttpServletRequest request = attributes.getRequest();
        // The same address the rate limits use: behind a proxy, only right with server.forward-headers-strategy
        return new ClientInfo(request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
    }
}
