package com.codetea.ctantiddos;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WhitelistManager {
    private final Set<String> ipWhitelist = new HashSet<>();
    public WhitelistManager(FileConfiguration config) {
        List<String> ips = config.getStringList("whitelist.ips");
        if (ips != null) ipWhitelist.addAll(ips);
    }
    public boolean isIpWhitelisted(String ip) {
        return ipWhitelist.contains(ip);
    }
} 