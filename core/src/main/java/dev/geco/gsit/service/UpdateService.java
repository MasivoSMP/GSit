package dev.geco.gsit.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.geco.gsit.GSitMain;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.logging.Level;

public class UpdateService {

    private final String GITHUB_REMOTE_URL = "https://api.github.com/repos/gecolay/gsit/releases/latest";
    private final String MODRINTH_REMOTE_URL = "https://api.modrinth.com/v2/project/gsit/version";
    private final String SPIGOT_REMOTE_URL = "https://api.spigotmc.org/legacy/update.php?resource=62325";
    private final String PAPER_REMOTE_URL = "https://hangar.papermc.io/api/v1/projects/gecolay/gsit/latest?channel=release";
    private final GSitMain gSitMain;
    private LocalDate lastCheckDate = null;
    private String latestVersion = null;
    private boolean isLatestVersion = true;

    public UpdateService(GSitMain gSitMain) {
        this.gSitMain = gSitMain;
    }

    public void checkForUpdates() {
        if(!gSitMain.getConfigService().CHECK_FOR_UPDATE) return;
        checkVersion(() -> {
            if(isLatestVersion) return;
            for(Player player : Bukkit.getOnlinePlayers()) {
                if(!gSitMain.getPermissionService().hasPermission(player, "Update")) continue;
                gSitMain.getMessageService().sendMessage(player, "Plugin.plugin-update", "%Name%", GSitMain.NAME, "%NewVersion%", latestVersion, "%Version%", gSitMain.getDescription().getVersion(), "%Path%", gSitMain.getDescription().getWebsite());
            }
            gSitMain.getMessageService().sendMessage(Bukkit.getConsoleSender(), "Plugin.plugin-update", "%Name%", GSitMain.NAME, "%NewVersion%", latestVersion, "%Version%", gSitMain.getDescription().getVersion(), "%Path%", gSitMain.getDescription().getWebsite());
        });
    }

    public void checkForUpdates(Player player) {
        if(!gSitMain.getConfigService().CHECK_FOR_UPDATE) return;
        if(!gSitMain.getPermissionService().hasPermission(player, "Update")) return;
        checkVersion(() -> {
            if(isLatestVersion) return;
            gSitMain.getMessageService().sendMessage(player, "Plugin.plugin-update", "%Name%", GSitMain.NAME, "%NewVersion%", latestVersion, "%Version%", gSitMain.getDescription().getVersion(), "%Path%", gSitMain.getDescription().getWebsite());
        });
    }

    private void getGitHubVersion(Consumer<String> versionConsumer) {
        gSitMain.getTaskService().run(() -> {
            try(InputStream inputStream = new URL(GITHUB_REMOTE_URL).openStream(); InputStreamReader reader = new InputStreamReader(inputStream)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String tag = json.get("tag_name").getAsString();
                if(tag != null && versionConsumer != null) versionConsumer.accept(tag);
            } catch(IOException e) {
                if (e.getMessage().contains("50")) return;
                gSitMain.getLogger().log(Level.WARNING, "Could not get github remote version!", e);
            }
        }, false);
    }

    private void getModrinthVersion(Consumer<String> versionConsumer) {
        gSitMain.getTaskService().run(() -> {
            try {
                URLConnection connection = new URL(MODRINTH_REMOTE_URL).openConnection();
                connection.setRequestProperty("User-Agent", GSitMain.NAME + "/" + gSitMain.getDescription().getVersion());
                try(InputStream inputStream = connection.getInputStream(); InputStreamReader reader = new InputStreamReader(inputStream)) {
                    JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();
                    if(jsonArray.isEmpty()) return;
                    String tag = jsonArray.get(0).getAsJsonObject().get("version_number").getAsString();
                    if(tag != null && versionConsumer != null) versionConsumer.accept(tag);
                }
            } catch(IOException e) {
                if (e.getMessage().contains("50")) return;
                gSitMain.getLogger().log(Level.WARNING, "Could not get modrinth remote version!", e);
            }
        }, false);
    }

    private void getSpigotVersion(Consumer<String> versionConsumer) {
        gSitMain.getTaskService().run(() -> {
            try(InputStream inputStream = new URL(SPIGOT_REMOTE_URL).openStream(); Scanner scanner = new Scanner(inputStream)) {
                if(scanner.hasNext() && versionConsumer != null) versionConsumer.accept(scanner.next());
            } catch(IOException e) {
                if(e.getMessage().contains("50")) return;
                gSitMain.getLogger().log(Level.WARNING, "Could not get spigot remote version!", e);
            }
        }, false);
    }

    private void getPaperVersion(Consumer<String> versionConsumer) {
        gSitMain.getTaskService().run(() -> {
            try(InputStream inputStream = new URL(PAPER_REMOTE_URL).openStream(); Scanner scanner = new Scanner(inputStream)) {
                if(scanner.hasNext() && versionConsumer != null) versionConsumer.accept(scanner.next());
            } catch(IOException e) {
                if(e.getMessage().contains("50")) return;
                gSitMain.getLogger().log(Level.WARNING, "Could not get paper remote version!", e);
            }
        }, false);
    }

    private void checkVersion(Runnable runnable) {
        LocalDate today = LocalDate.now();
        if(lastCheckDate != null && lastCheckDate.equals(today)) {
            runnable.run();
            return;
        }
        lastCheckDate = today;
        try {
            switch(gSitMain.getSource()) {
                case "github":
                    getGitHubVersion((version) -> setLatestVersion(version, runnable));
                    break;
                case "modrinth":
                    getModrinthVersion((version) -> setLatestVersion(version, runnable));
                    break;
                case "spigot":
                    getSpigotVersion((version) -> setLatestVersion(version, runnable));
                    break;
                case "paper":
                    getPaperVersion((version) -> setLatestVersion(version, runnable));
                    break;
            }
        } catch(Throwable e) {
            gSitMain.getLogger().log(Level.WARNING, "Could not check version!", e);
            isLatestVersion = true;
        }
    }

    private void setLatestVersion(String version, Runnable runnable) {
        latestVersion = version;
        if(latestVersion == null) {
            isLatestVersion = true;
            return;
        }
        String localVersion = gSitMain.getDescription().getVersion();
        String[] localVersionParts = localVersion.split("\\.");
        String[] remoteVersionParts = latestVersion.split("\\.");
        int minLength = Math.min(localVersionParts.length, remoteVersionParts.length);
        for(int i = 0; i < minLength; i++) {
            int localPart = Integer.parseInt(localVersionParts[i]);
            int remotePart = Integer.parseInt(remoteVersionParts[i]);
            if(localPart < remotePart) {
                isLatestVersion = false;
                runnable.run();
                return;
            } else if(localPart > remotePart) {
                isLatestVersion = true;
                return;
            }
        }
        isLatestVersion = localVersionParts.length >= remoteVersionParts.length;
        runnable.run();
    }

}