package com.edvexa.gateway.routing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gateway")
@Tag(name = "Gateway Routing", description = "Dynamic proxy and request forwarding entry point")
public class GatewayRoutingController {

    private final GatewayRoutingService routingService;

    public GatewayRoutingController(GatewayRoutingService routingService) {
        this.routingService = routingService;
    }

    @RequestMapping("/**")
    @Operation(summary = "Forward request to registered downstream service")
    public ResponseEntity<byte[]> handleGatewayRequest(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        String uri = request.getRequestURI();
        String subpath = uri.substring("/gateway".length());
        if (subpath.isEmpty()) {
            subpath = "/";
        }

        String clientId = (String) request.getAttribute(com.edvexa.gateway.security.apikey.ApiKeyAuthenticationFilter.ATTR_CLIENT_ID);
        if (clientId == null || clientId.isBlank()) {
            clientId = request.getHeader("X-Client-ID");
        }
        if (clientId == null || clientId.isBlank()) {
            clientId = request.getHeader("X-API-Key");
        }
        if (clientId == null || clientId.isBlank()) {
            clientId = "anonymous";
        }

        return routingService.forwardRequest(request, subpath, body, clientId);
    }
}
