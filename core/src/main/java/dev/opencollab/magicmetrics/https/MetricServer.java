package dev.opencollab.magicmetrics.https;

import com.sun.net.httpserver.*;
import dev.opencollab.magicmetrics.MagicMetrics;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.cert.CertificateException;

public class MetricServer {
    private final HttpsServer httpsServer;
    private final HttpServer httpServer;

    public MetricServer(MagicMetrics metrics) throws Exception {
        InetSocketAddress address = new InetSocketAddress(metrics.config().address(), metrics.config().port());
        if (metrics.config().keystorePath().isEmpty() || metrics.config().keystorePassword().isEmpty()) {
            if (metrics.config().keystorePath().isEmpty()) {
                metrics.logInfo("Keystore path is empty.");
            }
            if (metrics.config().keystorePassword().isEmpty()) {
                metrics.logInfo("Keystore password is empty.");
            }
            metrics.logInfo("Using HTTP server.");
            this.httpServer = HttpServer.create(address, 0);
            this.httpsServer = null;
            this.httpServer.start();
            return;
        }

        HttpsServer httpsServer;
        HttpServer httpServer = null;

        try {
            metrics.logInfo("Attempting to use HTTPS server.");
            httpsServer = HttpsServer.create(address, 0);
            SSLContext sslContext = SSLContext.getInstance("TLS");

            char[] password = metrics.config().keystorePassword().toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream(metrics.config().keystorePath());
            ks.load(fis, password);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    try {
                        SSLContext c = SSLContext.getDefault();
                        SSLEngine engine = c.createSSLEngine();
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());

                        SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
                        params.setSSLParameters(defaultSSLParameters);
                    } catch (Exception ex) {
                        metrics.logError("Failed to create HTTPS port", ex);
                    }
                }
            });
        } catch (CertificateException | FileNotFoundException e) {
            metrics.logError("Unable to start-up HTTPS server, falling back to HTTP server.", e);
            httpsServer = null;
            httpServer = HttpServer.create(address, 0);
        }

        this.httpsServer = httpsServer;
        this.httpServer = httpServer;

        if (this.httpsServer != null) this.httpsServer.start();
        else this.httpServer.start();
    }

    public void registerHandler(String path, HttpHandler handler) {
        if (this.httpsServer != null) this.httpsServer.createContext(path, handler);
        else this.httpServer.createContext(path, handler);
    }
}
