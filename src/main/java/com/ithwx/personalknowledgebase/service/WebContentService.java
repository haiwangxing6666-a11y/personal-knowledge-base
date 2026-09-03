package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.dto.WebPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebContentService {

    private static final String REMOVABLE_ELEMENTS =
            "script,style,noscript,svg,nav,footer,header,aside,form";
    private static final Pattern CHARSET_PATTERN =
            Pattern.compile("charset\\s*=\\s*[\\\"']?([^;\\s\\\"']+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final int maxContentBytes;
    private final Duration requestTimeout;

    public WebContentService(
            @Value("${app.web.max-content-bytes:2097152}") int maxContentBytes,
            @Value("${app.web.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${app.web.request-timeout-seconds:10}") int requestTimeoutSeconds
    ) {
        if (maxContentBytes <= 0 || connectTimeoutSeconds <= 0 || requestTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("网页抓取大小和超时配置必须大于 0");
        }

        this.maxContentBytes = maxContentBytes;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public WebPage fetch(String rawUrl) {
        URI uri = parseUrl(rawUrl);

        try {
            validatePublicHttpUrl(uri);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .header("User-Agent", "PersonalKnowledgeBaseBot/1.0")
                    .header("Accept", "text/html,text/plain;q=0.9")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            validateResponse(response);
            byte[] content = readLimited(response.body());
            String contentType = response.headers()
                    .firstValue("content-type")
                    .orElse("");

            return parseContent(uri, contentType, content);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("抓取网页被中断", exception);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("抓取网页失败：" + safeMessage(exception), exception);
        }
    }

    WebPage parseContent(URI uri, String contentType, byte[] content) {
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        boolean htmlContent = normalizedContentType.contains("text/html");
        boolean plainTextContent = normalizedContentType.contains("text/plain");

        if (!htmlContent && !plainTextContent) {
            throw new IllegalArgumentException("网页内容类型不受支持：" + contentType);
        }

        String rawText = new String(content, resolveCharset(contentType));

        if (plainTextContent) {
            String text = rawText.strip();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("网页中没有可提取的正文");
            }
            return new WebPage(uri.toString(), uri.getHost(), text);
        }

        org.jsoup.nodes.Document document = Jsoup.parse(rawText, uri.toString());
        document.select(REMOVABLE_ELEMENTS).remove();

        Element main = document.selectFirst("main,article,[role=main]");
        Element contentRoot = main == null ? document.body() : main;
        String text = contentRoot == null ? "" : contentRoot.text().strip();

        if (text.isEmpty()) {
            throw new IllegalArgumentException("网页中没有可提取的正文");
        }

        String title = document.title().isBlank() ? uri.getHost() : document.title().strip();
        return new WebPage(uri.toString(), title, text);
    }

    private URI parseUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("网页地址不能为空");
        }

        try {
            return URI.create(rawUrl.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("网页地址格式不正确", exception);
        }
    }

    private void validatePublicHttpUrl(URI uri) throws IOException {
        String scheme = uri.getScheme();
        boolean supportedProtocol = "http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme);

        if (!supportedProtocol || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("只支持公开的 http/https 网址");
        }

        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (isPrivateOrLocal(address)) {
                throw new IllegalArgumentException("不允许访问本机或内网地址");
            }
        }
    }

    private boolean isPrivateOrLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        boolean uniqueLocalIpv6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;

        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || uniqueLocalIpv6;
    }

    private void validateResponse(HttpResponse<InputStream> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("网页返回状态码 " + response.statusCode());
        }

        long declaredLength = response.headers()
                .firstValueAsLong("content-length")
                .orElse(-1);
        if (declaredLength > maxContentBytes) {
            throw new IllegalArgumentException("网页正文超过大小限制");
        }
    }

    private byte[] readLimited(InputStream body) throws IOException {
        try (InputStream inputStream = body) {
            byte[] bytes = inputStream.readNBytes(maxContentBytes + 1);
            if (bytes.length > maxContentBytes) {
                throw new IllegalArgumentException("网页正文超过大小限制");
            }
            return bytes;
        }
    }

    private Charset resolveCharset(String contentType) {
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (IllegalArgumentException ignored) {
                // 未知编码时使用 UTF-8。
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
