package icu.yeguo.apigateway;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import icu.yeguo.apicommon.model.entity.User;
import icu.yeguo.apicommon.service.CommonService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.commons.fileupload.MultipartStream;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
@Order(-1)
public class CustomGlobalFilter implements GlobalFilter {
    private static final String GATEWAY = "http://localhost:8082";
    @DubboReference
    private CommonService commonService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            ServerHttpRequest request = exchange.getRequest();
            logRequestDetails(request);
            String requestUrl = GATEWAY + request.getPath();
            log.info("请求接口URL:" + requestUrl);
            Long interfaceInfoId = commonService.getInterfaceInfoId(requestUrl);
            if (interfaceInfoId == null) {
                log.error("接口不存在");
                exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                return exchange.getResponse().setComplete();
            }

            if (HttpMethod.POST.equals(request.getMethod())) {
                return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String bodyString = new String(bytes, StandardCharsets.UTF_8);
                    log.info("requestBody = {}", bodyString);

                    Map<String, String> formData = parseMultipartFormData(bytes,
                            Objects.requireNonNull(request.getHeaders().getContentType()).toString());
                    String accessKey = formData.get("accessKey");
                    String signature = formData.get("signature");

                    if (accessKey == null || signature == null) {
                        log.error("POST请求中缺少 accessKey 或 signature 参数");
                        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                        return exchange.getResponse().setComplete();
                    }

                    log.info("POST请求参数：accessKey=" + accessKey + ", signature=" + signature);

                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.defer(() -> Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
                        }
                    };

                    return processRequest(exchange.mutate().request(mutatedRequest).build(), chain, accessKey,
                            signature, interfaceInfoId);
                });
            } else {
                MultiValueMap<String, String> queryParams = request.getQueryParams();
                String accessKey = queryParams.getFirst("accessKey");
                String signature = queryParams.getFirst("signature");
                log.info("GET请求参数：accessKey=" + accessKey + ", signature=" + signature);
                return processRequest(exchange, chain, accessKey, signature, interfaceInfoId);
            }
        } catch (Exception e) {
            log.error("过滤器处理请求时发生异常", e);
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }

    private void logRequestDetails(ServerHttpRequest request) {
        log.info("请求id:" + request.getId());
        log.info("请求方法:" + request.getMethod());
        log.info("请求接口URI:" + request.getURI());
        log.info("请求路径:" + request.getPath());
        log.info("请求Cookies:" + request.getCookies());
        log.info("请求参数:" + request.getQueryParams());
        log.info("本地网关地址:" + request.getLocalAddress());
        log.info("远程请求地址:" + request.getRemoteAddress());
        log.info("请求头:" + request.getHeaders());
    }

    private Mono<Void> processRequest(ServerWebExchange exchange, GatewayFilterChain chain,
                                      String accessKey, String signature, Long interfaceInfoId) {
        log.info("accessKey:" + accessKey);
        log.info("signature:" + signature);
        try {
            User user = commonService.getUser(accessKey);
            if (user == null) {
                log.info("用户不存在");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String message = user.getAccessKey() + user.getSecretKey();
            String generatedSignature = commonService.generateSignature(message);
            log.info("服务端验证签名:" + generatedSignature);

            if (!generatedSignature.equals(signature)) {
                log.info("签名不一致");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return handleResponse(exchange, chain, interfaceInfoId, user);
        } catch (Exception e) {
            log.error("处理请求时发生异常", e);
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }

    private Map<String, String> parseMultipartFormData(byte[] bodyBytes, String contentType) {
        Map<String, String> formData = new HashMap<>();
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bodyBytes);
            MultipartStream multipartStream = new MultipartStream(inputStream,
                    getBoundary(contentType).getBytes(StandardCharsets.UTF_8), 4096, null);
            boolean nextPart = multipartStream.skipPreamble();
            while (nextPart) {
                String header = multipartStream.readHeaders();
                if (header.contains("form-data; name=\"accessKey\"")) {
                    String accessKey = readMultipartContentAsString(multipartStream);
                    formData.put("accessKey", accessKey);
                } else if (header.contains("form-data; name=\"signature\"")) {
                    String signature = readMultipartContentAsString(multipartStream);
                    formData.put("signature", signature);
                } else {
                    multipartStream.discardBodyData();
                }
                nextPart = multipartStream.readBoundary();
            }
        } catch (IOException e) {
            log.error("解析multipart/form-data请求体时发生错误", e);
        }
        return formData;
    }

    private String readMultipartContentAsString(MultipartStream multipartStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        multipartStream.readBodyData(outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private String getBoundary(String contentType) {
        String boundary = null;
        String[] params = contentType.split(";");
        for (String param : params) {
            param = param.trim();
            if (param.startsWith("boundary=")) {
                boundary = param.substring("boundary=".length());
                break;
            }
        }
        return boundary;
    }

    public Mono<Void> handleResponse(ServerWebExchange exchange, GatewayFilterChain chain,
                                     Long interfaceInfoId, User user) {
        try {
            ServerHttpResponse originalResponse = exchange.getResponse();
            HttpStatus statusCode = (HttpStatus) originalResponse.getStatusCode();
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();

            log.info("响应日志 ============================================");
            log.info("HttpStatusCode:" + statusCode);

            if (statusCode == HttpStatus.OK) {
                ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                    @Override
                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                        log.info("body instanceof Flux: {}", (body instanceof Flux));

                        if (body instanceof Flux) {
                            Flux<DataBuffer> fluxBody = (Flux<DataBuffer>) body;

                            return DataBufferUtils.join(fluxBody)
                                    .flatMap(dataBuffer -> {
                                        byte[] content = new byte[dataBuffer.readableByteCount()];
                                        dataBuffer.read(content);
                                        DataBufferUtils.release(dataBuffer);

                                        String data = new String(content, StandardCharsets.UTF_8);
                                        log.info("响应结果：" + data);
                                        Response response = null;

                                        if (data.contains("svg")) {
                                            invoking(interfaceInfoId, user);
                                        } else {
                                            try {
                                                response = JSON.parseObject(data, Response.class);
                                            } catch (JSONException e) {
                                                log.error("JSON解析错误: ", e);
                                            }

                                            if (response != null && response.getCode() == 200) {
                                                invoking(interfaceInfoId, user);
                                            }
                                        }

                                        DataBuffer newBuffer = bufferFactory.wrap(content);
                                        return super.writeWith(Mono.just(newBuffer));
                                    });
                        } else {
                            return super.writeWith(body);
                        }
                    }

                };
                return chain.filter(exchange.mutate().response(decoratedResponse).build());
            }
            return chain.filter(exchange);
        } catch (Exception e) {
            log.error("处理响应时发生异常", e);
            return chain.filter(exchange);
        }
    }

    private void invoking(Long interfaceInfoId, User user) {
        try {
            Integer i = commonService.deductGoldCoin(interfaceInfoId, user);
            if (i == -1) {
                log.warn(user.getId() + ":扣除金币失败");
            }

            Long count = commonService.invokingCount(interfaceInfoId);
            if (count < 0) {
                log.warn("接口调用次数更新失败====接口id为：" + interfaceInfoId);
            }
            log.info("接口" + interfaceInfoId + "调用次数：" + count);
        } catch (Exception e) {
            log.error("调用接口时发生异常", e);
        }
    }
}
