package com.apix.app;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

public final class LocalStreamServer extends NanoHTTPD {

    private static final String TAG = "LocalProxy";
    public static final int PORT = 8080;
    public static final String HOST = "127.0.0.1";
    private static final String APP_SALT = "Apix_Enterprise_Salt_2026_"; 

    private static final Pattern HLS_URI_PATTERN = Pattern.compile("URI=['\"]([^'\"]+)['\"]");

    // ── 1. إدارة الذاكرة الاحترافية (TTL Cache & Background Cleaner) ──
    private static class CacheEntry {
        final String url;
        final long expireAt;
        CacheEntry(String url, long expireAt) {
            this.url = url;
            this.expireAt = expireAt;
        }
    }

    private static final ConcurrentHashMap<String, CacheEntry> urlMap = new ConcurrentHashMap<>();
    private static ScheduledExecutorService cleanupExecutor;
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(15);

    // ── 2. طبقة الـ Retries & Exponential Backoff (Circuit Breaker مبسط) ──
    private static final Interceptor retryInterceptor = chain -> {
        Request request = chain.request();
        okhttp3.Response response = null;
        IOException exception = null;
        int tryCount = 0;
        int maxRetries = 3;

        while (tryCount < maxRetries) {
            try {
                response = chain.proceed(request);
                if (response.isSuccessful() || (response.code() >= 400 && response.code() < 500)) {
                    return response;
                }
                response.close();
            } catch (IOException e) {
                exception = e;
            }
            
            tryCount++;
            if (tryCount < maxRetries) {
                try { Thread.sleep((long) Math.pow(2, tryCount) * 250); } catch (InterruptedException ignored) {}
            }
        }
        if (exception != null) throw exception;
        return chain.proceed(request);
    };

    // ── 3. محرك HTTP متقدم مع Connection Pool و Timeouts متكيفة ──
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
            .addInterceptor(retryInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build();

    private static LocalStreamServer INSTANCE;
    private static volatile Map<String, String> upstreamHeaders = new HashMap<>();

    private LocalStreamServer() {
        super(HOST, PORT);
        startCleanupTask();
    }

    private void startCleanupTask() {
        if (cleanupExecutor == null || cleanupExecutor.isShutdown()) {
            cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
            cleanupExecutor.scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<String, CacheEntry>> it = urlMap.entrySet().iterator();
                while (it.hasNext()) {
                    if (now > it.next().getValue().expireAt) {
                        it.remove();
                    }
                }
            }, 5, 5, TimeUnit.MINUTES);
        }
    }

    public static synchronized LocalStreamServer ensureStarted() {
        try {
            if (INSTANCE == null) {
                INSTANCE = new LocalStreamServer();
            }
            if (!INSTANCE.isAlive()) {
                INSTANCE.start(SOCKET_READ_TIMEOUT, true);
                Log.d(TAG, "Enterprise Proxy started on http://" + HOST + ":" + PORT);
            }
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
        }
        return INSTANCE;
    }

    public static synchronized void shutdownServer() {
        try {
            if (cleanupExecutor != null) cleanupExecutor.shutdownNow();
            if (INSTANCE != null && INSTANCE.isAlive()) INSTANCE.stop();
        } catch (Exception ignored) {}
    }

    public static void setHeaders(Map<String, String> headers) {
        upstreamHeaders = headers != null ? headers : new HashMap<>();
    }

    public static boolean shouldBypass(String url) {
        if (url == null) return true;
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        String path = q >= 0 ? u.substring(0, q) : u;
        return path.endsWith(".mp4") || path.endsWith(".mkv");
    }

    private static String storeUrl(String realUrl) {
        if (realUrl == null || realUrl.isEmpty()) return "";
        String id = UUID.nameUUIDFromBytes((APP_SALT + realUrl).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        long expireTime = System.currentTimeMillis() + CACHE_TTL_MS;
        urlMap.put(id, new CacheEntry(realUrl, expireTime));
        return id;
    }

    public static String wrap(String realUrl) {
        if (shouldBypass(realUrl)) return realUrl;
        ensureStarted();
        return "http://" + HOST + ":" + PORT + "/play?id=" + storeUrl(realUrl);
    }

    // ───────────────────────────── serve ─────────────────────────────

    @Override
    public fi.iki.elonen.NanoHTTPD.Response serve(IHTTPSession session) {
        try {
            Method method = session.getMethod();

            if (Method.OPTIONS.equals(method)) {
                fi.iki.elonen.NanoHTTPD.Response resp = newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, "text/plain", "");
                resp.addHeader("Access-Control-Allow-Origin", "*");
                resp.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
                return resp;
            }

            String uri = session.getUri();
            
            if (uri.startsWith("/proxy/")) {
                String remainder = uri.substring(7);
                int slashIdx = remainder.indexOf('/');
                if (slashIdx < 0) return newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "invalid path");
                
                String id = remainder.substring(0, slashIdx);
                String path = remainder.substring(slashIdx + 1);
                
                CacheEntry entry = urlMap.get(id);
                if (entry == null) return newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "expired session");

                String baseUrl = entry.url;
                String targetUrl;
                try {
                    targetUrl = URI.create(baseUrl).resolve(path).toString();
                } catch (Exception e) {
                    targetUrl = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + path;
                }

                if (session.getQueryParameterString() != null && !session.getQueryParameterString().isEmpty()) {
                    targetUrl += (targetUrl.contains("?") ? "&" : "?") + session.getQueryParameterString();
                }

                return servePassthrough(targetUrl, session, method);
            }

            if ("/play".equals(uri)) {
                String id = session.getParms().get("id");
                if (id == null) return newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "missing id");
                CacheEntry entry = urlMap.get(id);
                if (entry == null) return newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "expired id");
                return serveManifest(entry.url, session, method);
            }

            return newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "not found");
        } catch (Exception e) {
            Log.e(TAG, "serve error", e);
            return newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "proxy error");
        }
    }

    // ─────────────────────── manifest rewriting ───────────────────────

    private fi.iki.elonen.NanoHTTPD.Response serveManifest(String manifestUrl, IHTTPSession session, Method method) throws Exception {
        Request.Builder reqBuilder = new Request.Builder().url(manifestUrl);
        
        if (session != null && session.getHeaders() != null) {
            Map<String, String> clientHeaders = session.getHeaders();
            String[] passthroughHeaders = {"cookie", "authorization", "origin", "referer", "user-agent", "accept-encoding"};
            for (String h : passthroughHeaders) {
                if (clientHeaders.containsKey(h)) reqBuilder.header(h, clientHeaders.get(h));
            }
        }
        for (Map.Entry<String, String> e : upstreamHeaders.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) reqBuilder.header(e.getKey(), e.getValue());
        }

        if (Method.HEAD.equals(method)) reqBuilder.head();

        OkHttpClient manifestClient = httpClient.newBuilder()
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        // ── استخدام okhttp3.Response صراحة لمنع التعارض ──
        try (okhttp3.Response response = manifestClient.newCall(reqBuilder.build()).execute()) {
            int code = response.code();
            if (!response.isSuccessful()) {
                return newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.lookup(code) != null ? fi.iki.elonen.NanoHTTPD.Response.Status.lookup(code) : fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "upstream error " + code);
            }

            if (Method.HEAD.equals(method)) {
                fi.iki.elonen.NanoHTTPD.Response resp = newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, response.header("Content-Type", "application/octet-stream"), "");
                resp.addHeader("Access-Control-Allow-Origin", "*");
                return resp;
            }

            ResponseBody body = response.body();
            String rawContent = body != null ? body.string() : "";
            String contentType = response.header("Content-Type", "");

            boolean dash = manifestUrl.toLowerCase().contains(".mpd") || contentType.contains("dash");
            String rewritten = dash ? rewriteDash(rawContent, manifestUrl) : rewriteHls(rawContent, manifestUrl);

            String ct = dash ? "application/dash+xml" : "application/vnd.apple.mpegurl";
            fi.iki.elonen.NanoHTTPD.Response resp = newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, ct, rewritten);
            resp.addHeader("Access-Control-Allow-Origin", "*");
            resp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            return resp;
        }
    }

    private String encryptUrlAndCreateProxyPath(String absoluteUrl) {
        int lastSlash = absoluteUrl.lastIndexOf('/');
        String base = lastSlash >= 0 ? absoluteUrl.substring(0, lastSlash + 1) : absoluteUrl + "/";
        String path = lastSlash >= 0 ? absoluteUrl.substring(lastSlash + 1) : "";
        
        String id = storeUrl(base);
        return "http://" + HOST + ":" + PORT + "/proxy/" + id + "/" + path;
    }

    private String rewriteDash(String xmlBody, String manifestUrl) {
        try {
            String baseDir = manifestUrl;
            int q = baseDir.indexOf('?');
            if (q >= 0) baseDir = baseDir.substring(0, q);
            int lastSlash = baseDir.lastIndexOf('/');
            baseDir = lastSlash >= 0 ? baseDir.substring(0, lastSlash + 1) : baseDir + "/";

            String baseId = storeUrl(baseDir);
            String proxyBaseUrl = "http://" + HOST + ":" + PORT + "/proxy/" + baseId + "/";

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlBody)));

            Element root = doc.getDocumentElement();
            
            boolean hasRootBaseUrl = false;
            NodeList rootChildren = root.getChildNodes();
            for (int i = 0; i < rootChildren.getLength(); i++) {
                Node child = rootChildren.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && "BaseURL".equals(child.getNodeName())) {
                    child.setTextContent(proxyBaseUrl);
                    hasRootBaseUrl = true;
                    break;
                }
            }
            if (!hasRootBaseUrl) {
                Element localBase = doc.createElement("BaseURL");
                localBase.setTextContent(proxyBaseUrl);
                root.insertBefore(localBase, root.getFirstChild());
            }

            traverseAndRewriteNodes(root);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();

        } catch (Exception e) {
            Log.e(TAG, "DOM parsing failed, falling back", e);
            return xmlBody;
        }
    }

    private void traverseAndRewriteNodes(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) node;
            String tagName = el.getTagName();

            if (!("BaseURL".equals(tagName) && node.getParentNode() != null && "MPD".equals(node.getParentNode().getNodeName()))) {
                if ("BaseURL".equals(tagName) || "Location".equals(tagName) || "SegmentURL".equals(tagName)) {
                    String content = el.getTextContent().trim();
                    if (content.startsWith("http://") || content.startsWith("https://")) {
                        el.setTextContent(encryptUrlAndCreateProxyPath(content));
                    }
                }
            }

            String[] attrs = {"media", "initialization", "sourceURL", "index", "xlink:href"};
            for (String attr : attrs) {
                if (el.hasAttribute(attr)) {
                    String val = el.getAttribute(attr);
                    if (val.startsWith("http://") || val.startsWith("https://")) {
                        el.setAttribute(attr, encryptUrlAndCreateProxyPath(val));
                    }
                }
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            traverseAndRewriteNodes(children.item(i));
        }
    }

    private String rewriteHls(String body, String manifestUrl) {
        String[] lines = body.split("\n", -1);
        StringBuilder out = new StringBuilder(body.length() + 256);
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) { out.append('\n'); continue; }

            if (line.startsWith("#")) {
                Matcher m = HLS_URI_PATTERN.matcher(line);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    String uri = m.group(1);
                    String abs = resolve(manifestUrl, uri);
                    String proxyPath = encryptUrlAndCreateProxyPath(abs);
                    m.appendReplacement(sb, "URI=\"" + Matcher.quoteReplacement(proxyPath) + "\"");
                }
                m.appendTail(sb);
                out.append(sb.toString()).append('\n');
            } else {
                String abs = resolve(manifestUrl, line);
                out.append(encryptUrlAndCreateProxyPath(abs)).append('\n');
            }
        }
        return out.toString();
    }

    // ───────────────────────── passthrough ─────────────────────────

    private fi.iki.elonen.NanoHTTPD.Response servePassthrough(String url, IHTTPSession session, Method method) throws Exception {
        Request.Builder reqBuilder = new Request.Builder().url(url);
        
        if (session != null && session.getHeaders() != null) {
            Map<String, String> clientHeaders = session.getHeaders();
            String[] criticalHeaders = {"range", "cookie", "authorization", "origin", "referer", "user-agent", "if-none-match", "if-modified-since"};
            for (String h : criticalHeaders) {
                if (clientHeaders.containsKey(h)) reqBuilder.header(h, clientHeaders.get(h));
            }
        }
        for (Map.Entry<String, String> e : upstreamHeaders.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) reqBuilder.header(e.getKey(), e.getValue());
        }

        if (Method.HEAD.equals(method)) reqBuilder.head();

        // ── استخدام okhttp3.Response صراحة لمنع التعارض ──
        okhttp3.Response response = httpClient.newCall(reqBuilder.build()).execute();
        int code = response.code();
        
        if (Method.HEAD.equals(method)) {
            fi.iki.elonen.NanoHTTPD.Response resp = newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.lookup(code) != null ? fi.iki.elonen.NanoHTTPD.Response.Status.lookup(code) : fi.iki.elonen.NanoHTTPD.Response.Status.OK, response.header("Content-Type", "application/octet-stream"), "");
            for (String headerName : response.headers().names()) {
                if (!headerName.equalsIgnoreCase("content-length") && !headerName.equalsIgnoreCase("content-type")) {
                    resp.addHeader(headerName, response.header(headerName));
                }
            }
            resp.addHeader("Access-Control-Allow-Origin", "*");
            response.close();
            return resp;
        }

        ResponseBody body = response.body();
        InputStream upstreamIn = body != null ? body.byteStream() : new ByteArrayInputStream(new byte[0]);
        
        final okhttp3.Response finalResponse = response;
        InputStream safeIn = new FilterInputStream(upstreamIn) {
            @Override
            public void close() throws IOException {
                super.close();
                finalResponse.close();
            }
        };

        String ct = response.header("Content-Type", "application/octet-stream");
        long len = body != null ? body.contentLength() : -1;

        fi.iki.elonen.NanoHTTPD.Response.IStatus status = (code == 206) ? fi.iki.elonen.NanoHTTPD.Response.Status.PARTIAL_CONTENT : fi.iki.elonen.NanoHTTPD.Response.Status.OK;
        if (code >= 400) {
            status = fi.iki.elonen.NanoHTTPD.Response.Status.lookup(code) != null ? fi.iki.elonen.NanoHTTPD.Response.Status.lookup(code) : fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR;
        }

        fi.iki.elonen.NanoHTTPD.Response resp = (len >= 0) 
                ? newFixedLengthResponse(status, ct, safeIn, len) 
                : newChunkedResponse(status, ct, safeIn);

        for (String headerName : response.headers().names()) {
            if (!headerName.equalsIgnoreCase("content-length") && !headerName.equalsIgnoreCase("content-type")) {
                resp.addHeader(headerName, response.header(headerName));
            }
        }
        
        resp.addHeader("Access-Control-Allow-Origin", "*");
        return resp;
    }

    // ── دالة resolve المفقودة التي سببت الفشل ──
    private static String resolve(String base, String ref) {
        if (ref == null || ref.isEmpty()) return base;
        String low = ref.toLowerCase();
        if (low.startsWith("http://") || low.startsWith("https://")) return ref;
        try {
            return URI.create(base).resolve(ref).toString();
        } catch (Exception e) {
            return ref;
        }
    }
}
