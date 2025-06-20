package com.codetea.ctantiddos;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CTAntiDdos extends JavaPlugin implements Listener, TabExecutor, TabCompleter {
    private FileConfiguration config;
    private final Map<String, List<Long>> ipConnectTimes = new ConcurrentHashMap<>();
    private final Set<String> bannedIps = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Long, Integer> joinTimestamps = new HashMap<>();
    private Logger fileLogger;
    private WhitelistManager whitelistManager;
    private LogManager logManager;
    private enum ProtectionLevel { STRICT, NORMAL, RELAXED }
    private ProtectionLevel currentLevel = ProtectionLevel.NORMAL;
    private long lastAttackCheck = 0;
    private int recentAttackCount = 0;
    private final Map<String, Long> smartBotMonitor = new ConcurrentHashMap<>();
    private final Set<String> smartBotMoved = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> smartBotChatted = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> smartBotInteracted = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static class AttackEvent {
        final long time;
        final String type;
        AttackEvent(String type) { this.time = System.currentTimeMillis(); this.type = type; }
    }
    private final ConcurrentLinkedQueue<AttackEvent> attackEvents = new ConcurrentLinkedQueue<>();
    private boolean versionWarned = false;
    private String latestVersion = null;
    private String updateUrl = "https://api.github.com/repos/ZMF-1/CTAntiDdos/releases/latest";
    private double cachedTps = 20.0;
    private long lastTpsUpdate = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        whitelistManager = new WhitelistManager(config);
        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("ctad");
        if (cmd != null) cmd.setExecutor(this);
        getLogger().info("CTAntiDdos by CodeTea 已启用");
        setupFileLogger();
        startDynamicProtectionTask();
        checkCompatibility();
        checkUpdateAsync();
    }

    private void setupFileLogger() {
        if (config.getBoolean("log.enabled", true)) {
            try {
                fileLogger = Logger.getLogger("CTAntiDdosLog");
                File logFile = new File(getDataFolder(), "CTAntiDdos.log");
                int maxSize = config.getInt("log.max-size", 1048576);
                int maxArchives = config.getInt("log.max-archives", 5);
                logManager = new LogManager(logFile, maxSize, maxArchives);
                FileHandler fh = new FileHandler(logFile.getAbsolutePath(), true);
                fh.setFormatter(new SimpleFormatter());
                fileLogger.addHandler(fh);
            } catch (IOException e) {
                getLogger().warning("日志文件初始化失败: " + e.getMessage());
                e.printStackTrace();
                fileLogger = null; // 降级为控制台日志
            }
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("CTAntiDdos by CodeTea 已卸载");
    }

    private void startDynamicProtectionTask() {
        if (!config.getBoolean("dynamic-protection.enabled", false)) return;
        int interval = config.getInt("dynamic-protection.check-interval-seconds", 30);
        Bukkit.getScheduler().runTaskTimer(this, this::updateProtectionLevel, 20L, 20L * interval); // 检测周期可配置
    }

    private void updateProtectionLevel() {
        double tps = getCachedTps();
        int online = Bukkit.getOnlinePlayers().size();
        int attackRate = recentAttackCount;
        recentAttackCount = 0;
        double tpsStrict = config.getDouble("dynamic-protection.tps-thresholds.strict", 18.0);
        double tpsNormal = config.getDouble("dynamic-protection.tps-thresholds.normal", 19.0);
        int playerStrict = config.getInt("dynamic-protection.player-thresholds.strict", 100);
        int playerNormal = config.getInt("dynamic-protection.player-thresholds.normal", 50);
        int attackStrict = config.getInt("dynamic-protection.attack-rate-thresholds.strict", 10);
        int attackNormal = config.getInt("dynamic-protection.attack-rate-thresholds.normal", 3);
        ProtectionLevel level = ProtectionLevel.RELAXED;
        if (tps < tpsStrict || online > playerStrict || attackRate > attackStrict) {
            level = ProtectionLevel.STRICT;
        } else if (tps < tpsNormal || online > playerNormal || attackRate > attackNormal) {
            level = ProtectionLevel.NORMAL;
        }
        if (level != currentLevel) {
            currentLevel = level;
            log("动态防护切换至: " + currentLevel, "DYN");
        }
    }

    private double getCachedTps() {
        long now = System.currentTimeMillis();
        if (now - lastTpsUpdate > 10000) { // 10秒缓存
            cachedTps = getServerTps();
            lastTpsUpdate = now;
        }
        return cachedTps;
    }

    private double getServerTps() {
        try {
            Object minecraftServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] tps = (double[]) minecraftServer.getClass().getField("recentTps").get(minecraftServer);
            return tps[0];
        } catch (Exception e) {
            return 20.0;
        }
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        String ip = event.getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        if (whitelistManager.isIpWhitelisted(ip)) return;
        // 动态限流参数
        int max = getDynamicIpLimit();
        int interval = config.getInt("ip-limit.interval-seconds", 10);
        ipConnectTimes.putIfAbsent(ip, new ArrayList<>());
        List<Long> times = ipConnectTimes.get(ip);
        times.add(now);
        times.removeIf(t -> t < now - interval * 1000L);
        if (times.size() > max) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, getMsg("ip-limit"));
            log("IP限流: " + ip, "LIMIT");
            autoBan(ip, "IP限流");
            recentAttackCount++;
            return;
        }
        // 自动封禁
        if (bannedIps.contains(ip)) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, getMsg("banned"));
            log("已封禁IP尝试登录: " + ip, "BAN");
            return;
        }
        // DDoS消息
        if (config.getBoolean("ddos-message.enabled", true)) {
            String msg = config.getString("ddos-message.text", "§c[CTAntiDdos] 检测到异常流量，服务器已启动防护措施！");
            Bukkit.broadcastMessage(msg);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String ip = p.getAddress().getAddress().getHostAddress();
        if (whitelistManager.isIpWhitelisted(ip) || p.hasPermission("ctad.bypass")) return;
        // 动态Bot检测阈值
        long now = System.currentTimeMillis() / 1000L;
        int interval = config.getInt("bot-detection.interval-seconds", 5);
        int threshold = getDynamicBotThreshold();
        joinTimestamps.put(now, joinTimestamps.getOrDefault(now, 0) + 1);
        joinTimestamps.keySet().removeIf(t -> t < now - interval);
        int sum = joinTimestamps.values().stream().mapToInt(i -> i).sum();
        if (sum >= threshold) {
            Bukkit.broadcastMessage(getMsg("bot-detected"));
            for (Player player : Bukkit.getOnlinePlayers()) {
                String pip = player.getAddress().getAddress().getHostAddress();
                if (!player.hasPermission("ctad.bypass") && !whitelistManager.isIpWhitelisted(pip)) {
                    player.kickPlayer(getMsg("kick"));
                    log("Bot检测踢出: " + player.getName() + " (" + pip + ")", "BOT");
                    autoBan(pip, "Bot检测");
                }
            }
            recentAttackCount++;
        }
        // 智能Bot检测监控
        if (config.getBoolean("smart-bot-detection.enabled", false)) {
            if (!p.hasPermission("ctad.bypass") && !whitelistManager.isIpWhitelisted(p.getAddress().getAddress().getHostAddress())) {
                smartBotMonitor.put(p.getName(), System.currentTimeMillis());
                Bukkit.getScheduler().runTaskLater(this, () -> checkSmartBot(p), config.getInt("smart-bot-detection.monitor-seconds", 15) * 20L);
            }
        }
        // OP登录兼容性/更新提醒
        if (p.isOp()) {
            if (versionWarned) p.sendMessage("§c[CTAntiDdos] 检测到不兼容的服务器或Java版本，请检查控制台警告！");
            if (latestVersion != null && !latestVersion.equals(getDescription().getVersion())) {
                p.sendMessage("§e[CTAntiDdos] 检测到新版本：" + latestVersion + "，请前往GitHub更新！");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 可用于后续统计在线数等
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.getBoolean("smart-bot-detection.enabled", false)) return;
        smartBotMoved.add(event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!config.getBoolean("smart-bot-detection.enabled", false)) return;
        smartBotInteracted.add(event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!config.getBoolean("smart-bot-detection.enabled", false)) return;
        smartBotChatted.add(event.getPlayer().getName());
    }

    private void autoBan(String ip, String reason) {
        if (!config.getBoolean("auto-ban.enabled", true) || whitelistManager.isIpWhitelisted(ip)) return;
        bannedIps.add(ip);
        log("自动封禁IP: " + ip + " 原因: " + reason, "BAN");
        // 外部API
        if (config.getBoolean("external-firewall.enabled", false)) {
            String api = config.getString("external-firewall.api-url", "");
            if (api != null && !api.isEmpty()) {
                String url = api.replace("{ip}", ip);
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2000);
                    conn.getInputStream().close();
                } catch (Exception e) {
                    log("外部API调用失败: " + e.getMessage(), "API");
                }
            }
            String cmd = config.getString("external-firewall.command", "");
            if (cmd != null && !cmd.isEmpty()) {
                try {
                    Runtime.getRuntime().exec(cmd.replace("{ip}", ip));
                } catch (IOException e) {
                    log("外部命令执行失败: " + e.getMessage(), "API");
                }
            }
        }
        // 自动解封
        int banSec = config.getInt("auto-ban.ban-seconds", 600);
        Bukkit.getScheduler().runTaskLater(this, () -> bannedIps.remove(ip), banSec * 20L);
    }

    private void log(String msg, String type) {
        if (config.getBoolean("log.enabled", true) && fileLogger != null) {
            if (logManager != null) {
                try {
                    logManager.checkRotate();
                } catch (Exception e) {
                    getLogger().warning("日志归档异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            String format = config.getString("log.format", "[%time%] [%type%] %msg%");
            String out = format.replace("%time%", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()))
                    .replace("%type%", type).replace("%msg%", msg);
            fileLogger.info(out);
        } else {
            // 降级为控制台日志
            getLogger().info("[降级日志] [" + type + "] " + msg);
        }
    }

    private String getMsg(String key) {
        String lang = config.getString("lang", "zh");
        String msg = config.getString("messages." + lang + "." + key, null);
        if (msg == null) {
            msg = config.getString("messages.en." + key, "");
        }
        return msg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            config = getConfig();
            whitelistManager = new WhitelistManager(config);
            setupFileLogger();
            sender.sendMessage("§a[CTAntiDdos] 配置已重载。");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status") && config.getBoolean("commands.status-enabled", true)) {
            String level = currentLevel.name();
            double tps = getServerTps();
            int online = Bukkit.getOnlinePlayers().size();
            int attack = countRecentAttacks();
            String banned = bannedIps.toString();
            String version = getDescription().getVersion();
            sender.sendMessage(getMsg("status-header"));
            sender.sendMessage(getMsg("status-level").replace("%level%", level));
            sender.sendMessage(getMsg("status-tps").replace("%tps%", String.format("%.2f", tps)));
            sender.sendMessage(getMsg("status-online").replace("%online%", String.valueOf(online)));
            sender.sendMessage(getMsg("status-attack").replace("%attack%", String.valueOf(attack)));
            sender.sendMessage(getMsg("status-banned").replace("%banned%", banned));
            sender.sendMessage(getMsg("status-version").replace("%version%", version));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unban") && config.getBoolean("commands.unban-enabled", true)) {
            String ip = args[1];
            if (bannedIps.remove(ip)) {
                sender.sendMessage("§a[CTAntiDdos] 已解封IP: " + ip);
            } else {
                sender.sendMessage("§c[CTAntiDdos] 未找到该IP: " + ip);
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(getMsg("help-header"));
            sender.sendMessage(getMsg("help-reload"));
            sender.sendMessage(getMsg("help-status"));
            sender.sendMessage(getMsg("help-unban"));
            sender.sendMessage(getMsg("help-help"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("update")) {
            sender.sendMessage("§e[CTAntiDdos] 正在检查新版本...");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                checkUpdateAsync();
                Bukkit.getScheduler().runTask(this, () -> {
                    if (latestVersion != null && !latestVersion.equals(getDescription().getVersion())) {
                        sender.sendMessage("§e[CTAntiDdos] 检测到新版本：" + latestVersion + "，请前往GitHub更新！");
                    } else {
                        sender.sendMessage("§a[CTAntiDdos] 当前已是最新版本。");
                    }
                });
            });
            return true;
        }
        // 默认帮助
        sender.sendMessage(getMsg("help-header"));
        sender.sendMessage(getMsg("help-reload"));
        sender.sendMessage(getMsg("help-status"));
        sender.sendMessage(getMsg("help-unban"));
        sender.sendMessage(getMsg("help-help"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("help", "reload", "status", "unban");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unban")) {
            return new ArrayList<>(bannedIps);
        }
        return Collections.emptyList();
    }

    private int getDynamicIpLimit() {
        if (!config.getBoolean("dynamic-protection.enabled", false))
            return config.getInt("ip-limit.max-connections", 3);
        switch (currentLevel) {
            case STRICT:
                return config.getInt("dynamic-protection.ip-limit.strict", 2);
            case NORMAL:
                return config.getInt("dynamic-protection.ip-limit.normal", 3);
            case RELAXED:
            default:
                return config.getInt("dynamic-protection.ip-limit.relaxed", 5);
        }
    }

    private int getDynamicBotThreshold() {
        if (!config.getBoolean("dynamic-protection.enabled", false))
            return config.getInt("bot-detection.threshold", 5);
        switch (currentLevel) {
            case STRICT:
                return config.getInt("dynamic-protection.bot-threshold.strict", 3);
            case NORMAL:
                return config.getInt("dynamic-protection.bot-threshold.normal", 5);
            case RELAXED:
            default:
                return config.getInt("dynamic-protection.bot-threshold.relaxed", 8);
        }
    }

    private void checkSmartBot(Player p) {
        String name = p.getName();
        if (!p.isOnline()) return;
        if (!smartBotMonitor.containsKey(name)) return;
        boolean requireMove = config.getBoolean("smart-bot-detection.require-move", true);
        boolean requireChat = config.getBoolean("smart-bot-detection.require-chat", false);
        boolean requireInteract = config.getBoolean("smart-bot-detection.require-interact", false);
        int minScore = config.getInt("smart-bot-detection.min-score", 2);
        int moveScore = config.getInt("smart-bot-detection.score.move", 2);
        int chatScore = config.getInt("smart-bot-detection.score.chat", 1);
        int interactScore = config.getInt("smart-bot-detection.score.interact", 1);
        boolean moved = smartBotMoved.contains(name);
        boolean chatted = smartBotChatted.contains(name);
        boolean interacted = smartBotInteracted.contains(name);
        int score = 0;
        if (moved) score += moveScore;
        if (chatted) score += chatScore;
        if (interacted) score += interactScore;
        boolean kick = (requireMove && !moved) || (requireChat && !chatted) || (requireInteract && !interacted) || (score < minScore);
        if (kick) {
            p.kickPlayer(getMsg("smartbot"));
            log("智能Bot检测踢出: " + name + " (" + p.getAddress() + ")", "SMARTBOT");
        }
        smartBotMonitor.remove(name);
        smartBotMoved.remove(name);
        smartBotChatted.remove(name);
        smartBotInteracted.remove(name);
    }

    private void logAttack(String type) {
        attackEvents.add(new AttackEvent(type));
    }

    private int countRecentAttacks() {
        long now = System.currentTimeMillis();
        long threshold = now - 10 * 60 * 1000L;
        while (!attackEvents.isEmpty() && attackEvents.peek().time < threshold) {
            attackEvents.poll();
        }
        return attackEvents.size();
    }

    private void checkCompatibility() {
        String paper = Bukkit.getVersion();
        String javaVer = System.getProperty("java.version");
        boolean paperOk = paper.contains("Paper");
        boolean javaOk = javaVer.compareTo("17") >= 0;
        if (!paperOk || !javaOk) {
            String warn = "[CTAntiDdos] 警告：检测到不兼容的服务器或Java版本！建议使用 Paper 1.20.4+ 和 Java 17+。当前：" + paper + ", Java: " + javaVer;
            getLogger().warning(warn);
            versionWarned = true;
        }
    }

    private void checkUpdateAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new java.net.URL(updateUrl).openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(3000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                String json = sb.toString();
                int tagIdx = json.indexOf("\"tag_name\":");
                if (tagIdx != -1) {
                    int start = json.indexOf('"', tagIdx + 11) + 1;
                    int end = json.indexOf('"', start);
                    latestVersion = json.substring(start, end);
                }
            } catch (Exception e) {
                getLogger().warning("自动更新检测失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
} 