package de.skygames.skyevents;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SkyEvents – /elo, /elo toggle, /admin, /event, /event settings
 * Welche Datapack-Function welches Event / welcher Befehl ausführt,
 * stellst du in der config.yml ein (Abschnitte "elo", "admin" und "events").
 */
public class SkyEvents extends JavaPlugin implements Listener, TabExecutor {

    // Spalten im /event settings Menü: Minuten, um die die Zeit verschoben wird
    private static final int[] DELTA = {-1440, -60, -10, 0, 0, 0, 10, 60, 1440};
    private static final String[] DELTA_NAME = {"-1 Tag", "-1 Stunde", "-10 Minuten", "", "", "", "+10 Minuten", "+1 Stunde", "+1 Tag"};

    private ZoneId zone;
    private DateTimeFormatter fmt;
    private final Set<String> warned = new HashSet<>();

    /** Markiert unsere eigenen Menüs. */
    static class Menu implements InventoryHolder {
        final String type;
        Inventory inv;
        Menu(String type) { this.type = type; }
        @Override public Inventory getInventory() { return inv; }
    }

    // ------------------------------------------------------------ Start

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        for (String name : List.of("elo", "admin", "event")) {
            PluginCommand pc = getCommand(name);
            if (pc != null) { pc.setExecutor(this); pc.setTabCompleter(this); }
        }
        getServer().getPluginManager().registerEvents(this, this);
        updateTime(ZonedDateTime.now(zone));
        if (getConfig().getBoolean("time-scoreboard.aktiv", true) && getConfig().getBoolean("time-scoreboard.sidebar", true)) {
            Objective o = Bukkit.getScoreboardManager().getMainScoreboard().getObjective("time");
            if (o != null) o.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
    }

    private void loadSettings() {
        reloadConfig();
        zone = ZoneId.of(getConfig().getString("zeitzone", "Europe/Berlin"));
        fmt = DateTimeFormatter.ofPattern(getConfig().getString("datum-format", "dd.MM.yyyy HH:mm"));
    }

    // ------------------------------------------------------------ Befehle

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase() : "";
        boolean admin = s.hasPermission("skyevents.admin");
        switch (cmd.getName().toLowerCase()) {
            case "elo" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Nur für Spieler."); return true; }
                runCommands(getConfig().getStringList(sub.equals("toggle") ? "elo.toggle-befehle" : "elo.befehle"), p);
            }
            case "admin" -> {
                if (!admin) { s.sendMessage(c(getConfig().getString("keine-rechte", "&cDazu hast du keine Rechte."))); return true; }
                if (sub.equals("reload")) { loadSettings(); s.sendMessage(c("&aSkyEvents-Config neu geladen.")); return true; }
                if (s instanceof Player p) openAdmin(p);
            }
            case "event" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Nur für Spieler."); return true; }
                if (sub.equals("settings")) {
                    if (!admin) { p.sendMessage(c(getConfig().getString("keine-rechte", "&cDazu hast du keine Rechte."))); return true; }
                    openSettings(p);
                } else openEvents(p);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String l, String[] a) {
        if (a.length != 1) return List.of();
        List<String> options = switch (cmd.getName().toLowerCase()) {
            case "elo" -> List.of("toggle");
            case "event" -> s.hasPermission("skyevents.admin") ? List.of("settings") : List.<String>of();
            case "admin" -> List.of("reload");
            default -> List.<String>of();
        };
        return options.stream().filter(x -> x.startsWith(a[0].toLowerCase())).toList();
    }

    /** Führt Befehle als Konsole aus. %player% wird durch den Spieler ersetzt
     *  (ohne Spieler: einmal für jeden Online-Spieler). */
    private void runCommands(List<String> cmds, Player p) {
        for (String raw : cmds) {
            String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
            if (!cmd.contains("%player%")) { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd); continue; }
            if (p != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
            } else {
                for (Player t : Bukkit.getOnlinePlayers())
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", t.getName()));
            }
        }
    }

    // ------------------------------------------------------------ Menüs

    private void openAdmin(Player p) {
        Menu m = new Menu("admin");
        int size = Math.max(9, Math.min(54, getConfig().getInt("admin.groesse", 27) / 9 * 9));
        m.inv = Bukkit.createInventory(m, size, c(getConfig().getString("admin.titel", "&cAdmin-Menü")));
        ConfigurationSection sec = getConfig().getConfigurationSection("admin.knoepfe");
        if (sec != null) for (String k : sec.getKeys(false)) {
            ConfigurationSection b = sec.getConfigurationSection(k);
            if (b == null) continue;
            int slot = b.getInt("slot", -1);
            if (slot >= 0 && slot < size) m.inv.setItem(slot, item(b.getString("item", "STONE"), b.getString("name", k), b.getStringList("lore")));
        }
        fill(m.inv);
        p.openInventory(m.inv);
    }

    private void openEvents(Player p) {
        Menu m = new Menu("events");
        m.inv = Bukkit.createInventory(m, 27, c(getConfig().getString("event-menue-titel", "&6Events")));
        fillEvents(m.inv);
        p.openInventory(m.inv);
    }

    private void fillEvents(Inventory inv) {
        inv.clear();
        List<String> keys = eventKeys();
        int n = Math.min(keys.size(), 7);
        int[] slots = n == 1 ? new int[]{13} : n == 2 ? new int[]{11, 15} : n == 3 ? new int[]{11, 13, 15}
                : new int[]{10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < n; i++) inv.setItem(slots[i], eventItem(keys.get(i), false));
        inv.setItem(4, item("CLOCK", "&eJetzt: &f" + now().format(fmt), List.of()));
        fill(inv);
    }

    private void openSettings(Player p) {
        Menu m = new Menu("settings");
        int rows = Math.max(1, Math.min(6, eventKeys().size()));
        m.inv = Bukkit.createInventory(m, rows * 9, c(getConfig().getString("settings-menue-titel", "&cEvent-Einstellungen")));
        fillSettings(m.inv);
        p.openInventory(m.inv);
    }

    private void fillSettings(Inventory inv) {
        inv.clear();
        List<String> keys = eventKeys();
        for (int r = 0; r < keys.size() && r * 9 < inv.getSize(); r++) {
            String k = keys.get(r);
            int b = r * 9;
            for (int col : new int[]{0, 1, 2, 6, 7, 8}) {
                boolean minus = DELTA[col] < 0;
                inv.setItem(b + col, item(minus ? "RED_STAINED_GLASS_PANE" : "LIME_STAINED_GLASS_PANE",
                        (minus ? "&c" : "&a") + DELTA_NAME[col], List.of("&7für " + name(k))));
            }
            inv.setItem(b + 3, item("LIME_DYE", "&aJetzt starten", List.of("&7Startet " + name(k) + " &7sofort")));
            inv.setItem(b + 4, eventItem(k, true));
            inv.setItem(b + 5, item("BARRIER", "&cZurücksetzen", List.of("&7Event wieder schließen")));
        }
        fill(inv);
    }

    private ItemStack eventItem(String k, boolean admin) {
        ConfigurationSection s = ev(k);
        LocalDateTime t = eventTime(k);
        boolean next = !admin && k.equals(nextEvent());
        List<String> lore = new ArrayList<>();
        if (next) lore.add("&6» Nächstes Event «");
        lore.add("&7Datum: &f" + (t == null ? "nicht gesetzt" : t.format(fmt)));
        if (s.getBoolean("ausgefuehrt")) lore.add("&aIst offen!");
        else if (t == null) lore.add("&7Noch kein Termin");
        else lore.add("&eStartet in &f" + left(Duration.between(now(), t)));
        if (admin) lore.add("&8Zeit ändern schließt das Event wieder");
        ItemStack it = item(s.getString("item", "CLOCK"), s.getString("name", k), lore);
        if (next) {
            ItemMeta meta = it.getItemMeta();
            if (meta != null) { meta.setEnchantmentGlintOverride(true); it.setItemMeta(meta); }
        }
        return it;
    }

    // ------------------------------------------------------------ Klicks

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != m.inv) return;
        switch (m.type) {
            case "admin" -> clickAdmin(p, e.getSlot());
            case "settings" -> clickSettings(p, m, e.getSlot());
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    private void clickAdmin(Player p, int slot) {
        if (!p.hasPermission("skyevents.admin")) { later(p::closeInventory); return; }
        ConfigurationSection sec = getConfig().getConfigurationSection("admin.knoepfe");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection b = sec.getConfigurationSection(k);
            if (b == null || b.getInt("slot", -1) != slot) continue;
            sound(p, Sound.UI_BUTTON_CLICK);
            runCommands(b.getStringList("befehle"), p);
            String action = b.getString("aktion", "").toLowerCase();
            if (action.equals("event-settings")) later(() -> openSettings(p));
            else if (action.equals("events")) later(() -> openEvents(p));
            else if (action.startsWith("start:")) startEvent(action.substring(6));
            if (b.getBoolean("schliessen", false)) later(p::closeInventory);
            return;
        }
    }

    private void clickSettings(Player p, Menu m, int slot) {
        if (!p.hasPermission("skyevents.admin")) { later(p::closeInventory); return; }
        List<String> keys = eventKeys();
        int row = slot / 9, col = slot % 9;
        if (row >= keys.size() || col == 4) return;
        String k = keys.get(row);
        ConfigurationSection s = ev(k);
        LocalDateTime now = now().withSecond(0).withNano(0).plusMinutes(1);
        if (col == 3) {
            if (s.getBoolean("ausgefuehrt")) { p.sendMessage(c("&cDieses Event ist schon offen.")); return; }
            startEvent(k);
        } else if (col == 5) {
            LocalDateTime t = eventTime(k);
            s.set("ausgefuehrt", false);
            if (t == null || !t.isAfter(now)) s.set("zeit", now.plusHours(1).format(fmt));
            saveConfig();
            clearWarned(k);
            p.sendMessage(c(name(k) + " &7ist wieder geschlossen."));
        } else {
            LocalDateTime t = eventTime(k);
            if (t == null || t.isBefore(now)) t = now;
            t = t.plusMinutes(DELTA[col]);
            if (t.isBefore(now)) {
                p.sendMessage(c("&cDie Zeit läge in der Vergangenheit – nutze &e'Jetzt starten'&c."));
                sound(p, Sound.BLOCK_NOTE_BLOCK_BASS);
                return;
            }
            s.set("zeit", t.format(fmt));
            s.set("ausgefuehrt", false);
            saveConfig();
            clearWarned(k);
        }
        sound(p, Sound.UI_BUTTON_CLICK);
        fillSettings(m.inv);
    }

    // ------------------------------------------------------------ Events & Timer

    private void tick() {
        ZonedDateTime zdt = ZonedDateTime.now(zone);
        updateTime(zdt);
        LocalDateTime now = zdt.toLocalDateTime();
        for (String k : eventKeys()) {
            LocalDateTime t = eventTime(k);
            if (t == null || ev(k).getBoolean("ausgefuehrt")) continue;
            long sec = Duration.between(now, t).getSeconds();
            if (sec <= 0) { startEvent(k); continue; }
            for (int w : getConfig().getIntegerList("warnungen-minuten")) {
                if (sec <= w * 60L && sec > w * 60L - 60 && warned.add(k + ":" + w)) {
                    Bukkit.broadcastMessage(c(getConfig().getString("warnung", "%event% &7startet in &e%min% &7Minute(n)!")
                            .replace("%event%", name(k)).replace("%min%", String.valueOf(w))));
                }
            }
        }
        // offene Menüs live aktualisieren (Countdown)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m) {
                if (m.type.equals("events")) fillEvents(m.inv);
                else if (m.type.equals("settings")) fillSettings(m.inv);
            }
        }
    }

    private void startEvent(String k) {
        ConfigurationSection s = ev(k);
        if (s == null) return;
        s.set("ausgefuehrt", true);
        saveConfig();
        getLogger().info("Event gestartet: " + k);
        runCommands(s.getStringList("befehle"), null);
        String msg = s.getString("nachricht", "");
        if (!msg.isEmpty()) Bukkit.broadcastMessage(c(msg.replace("%event%", name(k))));
        for (Player p : Bukkit.getOnlinePlayers()) sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE);
    }

    /** Sperrt Portale in Dimensionen, deren Event noch nicht gestartet ist. */
    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        if (getConfig().getBoolean("admins-umgehen-sperre", true) && e.getPlayer().hasPermission("skyevents.admin")) return;
        World.Environment env = e.getTo().getWorld().getEnvironment();
        for (String k : eventKeys()) {
            ConfigurationSection s = ev(k);
            if (s.getBoolean("ausgefuehrt") || !s.getBoolean("portal-sperren", false)) continue;
            if (!env.name().equalsIgnoreCase(s.getString("dimension", ""))) continue;
            e.setCancelled(true);
            LocalDateTime t = eventTime(k);
            e.getPlayer().sendMessage(c(getConfig().getString("gesperrt", "%event% &cist noch nicht offen! &7Öffnet: &e%zeit%")
                    .replace("%event%", name(k)).replace("%zeit%", t == null ? "?" : t.format(fmt))));
            return;
        }
    }

    // ------------------------------------------------------------ Scoreboard "time"

    private void updateTime(ZonedDateTime n) {
        if (!getConfig().getBoolean("time-scoreboard.aktiv", true)) return;
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective o = sb.getObjective("time");
        if (o == null) o = sb.registerNewObjective("time", Criteria.DUMMY, "Zeit");
        String title = c(getConfig().getString("time-scoreboard.titel", "&6%datum%").replace("%datum%", n.format(fmt)));
        if (!title.equals(o.getDisplayName())) o.setDisplayName(title);
        o.getScore("Tag").setScore(n.getDayOfMonth());
        o.getScore("Monat").setScore(n.getMonthValue());
        o.getScore("Jahr").setScore(n.getYear());
        o.getScore("Stunde").setScore(n.getHour());
        o.getScore("Minute").setScore(n.getMinute());
    }

    // ------------------------------------------------------------ Helfer

    private List<String> eventKeys() {
        ConfigurationSection s = getConfig().getConfigurationSection("events");
        return s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false));
    }

    private ConfigurationSection ev(String k) { return getConfig().getConfigurationSection("events." + k); }

    private String name(String k) { return ev(k) == null ? k : ev(k).getString("name", k); }

    private LocalDateTime eventTime(String k) {
        try { return LocalDateTime.parse(ev(k).getString("zeit", ""), fmt); } catch (Exception ex) { return null; }
    }

    private String nextEvent() {
        String best = null;
        LocalDateTime bt = null;
        for (String k : eventKeys()) {
            LocalDateTime t = eventTime(k);
            if (t == null || ev(k).getBoolean("ausgefuehrt")) continue;
            if (bt == null || t.isBefore(bt)) { best = k; bt = t; }
        }
        return best;
    }

    private LocalDateTime now() { return LocalDateTime.now(zone); }

    private void clearWarned(String k) { warned.removeIf(w -> w.startsWith(k + ":")); }

    private static String left(Duration d) {
        long s = Math.max(0, d.getSeconds());
        long days = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        return (days > 0 ? days + "T " : "") + h + "h " + m + "m " + sec + "s";
    }

    private static String c(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }

    private static ItemStack item(String mat, String name, List<String> lore) {
        Material m = Material.matchMaterial(mat == null ? "" : mat);
        ItemStack it = new ItemStack(m == null ? Material.STONE : m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(name));
            List<String> l = new ArrayList<>();
            for (String line : lore) l.add(c(line));
            meta.setLore(l);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static void fill(Inventory inv) {
        ItemStack pane = item("GRAY_STAINED_GLASS_PANE", " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, pane);
    }

    private static void sound(Player p, Sound s) { p.playSound(p.getLocation(), s, 1f, 1f); }

    private void later(Runnable r) { Bukkit.getScheduler().runTask(this, r); }
}
