package net.feelix.stagecoach.bukkit.pack;

import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.feelix.stagecoach.bukkit.StagecoachPlugin;
import net.feelix.stagecoach.bukkit.config.StagecoachConfig;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ResourcePack implements Listener {
    /** One fixed id, so a client that has this version keeps it and our pack stacks with others. */
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("stagecoach:resource-pack".getBytes(StandardCharsets.UTF_8));
    private static final long ANSWER_TIMEOUT_SECONDS = 120;

    private final StagecoachPlugin plugin;
    private byte[] zip;
    private String sha1;
    private HttpServer http;
    private ExecutorService httpThreads;

    public ResourcePack(StagecoachPlugin plugin) {
        this.plugin = plugin;
    }

    /** Also copies the pack to the plugin folder for admins who host it elsewhere. */
    public void start() throws IOException {
        try (InputStream in = plugin.getResource("stagecoach-pack.zip")) {
            if (in == null) throw new IOException("stagecoach-pack.zip is missing from the plugin jar");
            zip = in.readAllBytes();
        }
        try {
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
        } catch (Exception e) {
            throw new IOException(e);
        }
        Path copy = plugin.getDataFolder().toPath().resolve("stagecoach-pack.zip");
        Files.createDirectories(copy.getParent());
        if (!Files.exists(copy) || !java.util.Arrays.equals(Files.readAllBytes(copy), zip)) Files.write(copy, zip);
        StagecoachConfig cfg = plugin.settings();
        if (!cfg.packSend()) {
            plugin.getLogger().info("Resource pack: not sent (resourcePack.send is false - the server's own pack must "
                    + "carry Stagecoach's assets, SHA-1 of ours " + sha1 + ")");
            return;
        }
        if (!cfg.packUrl().isEmpty()) {
            plugin.getLogger().info("Resource pack: players download it from " + cfg.packUrl() + " (SHA-1 " + sha1 + ")");
            return;
        }
        http = HttpServer.create(new InetSocketAddress(cfg.packPort()), 16);
        httpThreads = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Stagecoach pack server");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(httpThreads);
        String path = "/" + fileName();
        http.createContext("/", exchange -> {
            try (exchange) {
                if (!exchange.getRequestURI().getPath().equals(path)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, zip.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(zip);
                }
            }
        });
        http.start();
        plugin.getLogger().info("Resource pack: serving " + fileName() + " on port " + cfg.packPort());
    }

    public void stop() {
        if (http != null) http.stop(0);
        if (httpThreads != null) httpThreads.shutdownNow();
        http = null;
        httpThreads = null;
    }

    private String fileName() {
        return "stagecoach-" + sha1 + ".zip";
    }

    /** The admin's link, else our port on the address the player joined with. */
    URI uriFor(InetSocketAddress joinedWith) {
        StagecoachConfig cfg = plugin.settings();
        if (!cfg.packUrl().isEmpty()) return URI.create(cfg.packUrl());
        String host = !cfg.packHost().isEmpty() ? cfg.packHost()
                : joinedWith != null && joinedWith.getHostString() != null ? joinedWith.getHostString() : "localhost";
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]"; // an IPv6 address
        return URI.create("http://" + host + ":" + cfg.packPort() + "/" + fileName());
    }

    /** Holds the join until the client has the pack; a declined required pack disconnects by itself. */
    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (zip == null || !plugin.settings().packSend()) return;
        PlayerConfigurationConnection connection = event.getConnection();
        URI uri = uriFor(connection.getVirtualHost());
        CompletableFuture<ResourcePackStatus> answer = new CompletableFuture<>();
        connection.getAudience().sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, sha1))
                .required(plugin.settings().packRequired())
                .replace(false)
                .prompt(plugin.messages().c("pack-prompt"))
                .callback((id, status, audience) -> {
                    if (!status.intermediate()) answer.complete(status);
                }));
        String who = connection.getProfile().getName();
        ResourcePackStatus status = null;
        try {
            status = answer.get(ANSWER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            plugin.getLogger().warning(who + " didn't answer the resource pack within " + ANSWER_TIMEOUT_SECONDS + " s");
        } catch (Exception e) {
            plugin.getLogger().warning("Resource pack for " + who + " failed: " + e);
        }
        if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) return;
        if (status != null) plugin.getLogger().warning(who + "'s client answered the resource pack with " + status + " (" + uri + ")");
        // no pack means missing models, so no answer or a failed download keeps them out too
        if (plugin.settings().packRequired() && connection.isConnected()) {
            connection.disconnect(plugin.messages().c(status == ResourcePackStatus.FAILED_DOWNLOAD
                    || status == ResourcePackStatus.INVALID_URL ? "pack-download-failed" : "pack-no-answer"));
        }
    }
}
