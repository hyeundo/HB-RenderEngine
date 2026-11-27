package com.yourname.hb3d.paper;

import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class Hb3dWorldBackgroundPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final String CHANNEL = "hb3d:background_sync";
    private final BackgroundPacketEncoder encoder = new BackgroundPacketEncoder();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("hbbackground") != null) {
            getCommand("hbbackground").setExecutor(this);
            getCommand("hbbackground").setTabCompleter(this);
        }
        getServer().getScheduler().runTask(this,
                () -> getServer().getOnlinePlayers().forEach(this::syncPlayer));
        getLogger().info("HB3D Multiverse world backgrounds enabled");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        getServer().getScheduler().runTask(this, () -> syncPlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        getServer().getScheduler().runTask(this, () -> syncPlayer(event.getPlayer()));
    }

    private void syncPlayer(Player player) {
        if (!player.isOnline()) return;
        World world = player.getWorld();
        player.sendPluginMessage(this, CHANNEL, encoder.encode(world.getName(), load(world)));
    }

    private void syncWorld(World world) {
        world.getPlayers().forEach(this::syncPlayer);
    }

    private BackgroundPlacement load(World world) {
        ConfigurationSection section = getConfig().getConfigurationSection(path(world));
        if (section == null || !section.getBoolean("enabled", false)) return null;
        if (section.getString("model", "").isBlank()) return null;
        return new BackgroundPlacement(
                true,
                section.getString("model"),
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", -90.0),
                (float) section.getDouble("scale", 1.0));
    }

    private String path(World world) {
        return "worlds." + world.getName();
    }

    private void set(World world, String key, Object value) {
        getConfig().set(path(world) + "." + key, value);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            getServer().getWorlds().forEach(this::syncWorld);
            reply(sender, "설정을 다시 불러오고 모든 월드에 동기화했습니다.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            reply(sender, "reload 외의 명령어는 월드 안에서 실행해야 합니다.");
            return true;
        }
        if (args.length == 0) return help(player);

        World world = player.getWorld();
        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "set" -> {
                    if (args.length < 2) throw new IllegalArgumentException("Usage: /hbbackground set <model-id>");
                    String model = args[1];
                    validateModelId(model);
                    Location location = player.getLocation();
                    set(world, "enabled", true);
                    set(world, "model", model);
                    set(world, "x", location.getX());
                    set(world, "y", location.getY());
                    set(world, "z", location.getZ());
                    set(world, "yaw", 0.0);
                    set(world, "pitch", -90.0);
                    if (!getConfig().contains(path(world) + ".scale")) set(world, "scale", 1.0);
                    commit(world, player, "배경을 현재 위치에 배치했습니다: " + model);
                }
                case "position", "pos" -> {
                    requireConfigured(world);
                    Location location = player.getLocation();
                    set(world, "x", location.getX());
                    set(world, "y", location.getY());
                    set(world, "z", location.getZ());
                    commit(world, player, "배경 위치를 현재 위치로 변경했습니다.");
                }
                case "scale" -> {
                    requireConfigured(world);
                    if (args.length < 2) throw new IllegalArgumentException("사용법: /hbbackground scale <0.001~100>");
                    float scale = Float.parseFloat(args[1]);
                    if (!Float.isFinite(scale) || scale < 0.001F || scale > 100.0F) {
                        throw new IllegalArgumentException("스케일은 0.001~100 사이여야 합니다.");
                    }
                    set(world, "scale", scale);
                    commit(world, player, "배경 스케일을 " + scale + "로 변경했습니다.");
                }
                case "rotation", "rot" -> {
                    requireConfigured(world);
                    if (args.length < 3) throw new IllegalArgumentException("사용법: /hbbackground rotation <yaw> <pitch>");
                    float yaw = Float.parseFloat(args[1]);
                    float pitch = Float.parseFloat(args[2]);
                    if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("유효한 회전값이 아닙니다.");
                    set(world, "yaw", yaw);
                    set(world, "pitch", pitch);
                    commit(world, player, "배경 회전을 yaw=" + yaw + ", pitch=" + pitch + "로 변경했습니다.");
                }
                case "enable" -> {
                    requireConfigured(world);
                    set(world, "enabled", true);
                    commit(world, player, "이 월드의 배경을 활성화했습니다.");
                }
                case "disable" -> {
                    set(world, "enabled", false);
                    commit(world, player, "이 월드의 배경을 비활성화했습니다.");
                }
                case "status" -> showStatus(player, world);
                default -> { return help(player); }
            }
        } catch (IllegalArgumentException exception) {
            reply(player, ChatColor.RED + exception.getMessage());
        }
        return true;
    }

    private void commit(World world, CommandSender sender, String message) {
        saveConfig();
        syncWorld(world);
        reply(sender, message);
    }

    private void showStatus(CommandSender sender, World world) {
        BackgroundPlacement placement = load(world);
        if (placement == null) {
            reply(sender, world.getName() + ": 배경 비활성화");
            return;
        }
        reply(sender, world.getName() + ": model=" + placement.modelId()
                + " pos=" + placement.x() + "," + placement.y() + "," + placement.z()
                + " rotation=" + placement.yaw() + "," + placement.pitch()
                + " scale=" + placement.scale());
    }

    private boolean help(CommandSender sender) {
        reply(sender, "/hbbackground set <model-id> | position | scale <값> | rotation <yaw> <pitch>");
        reply(sender, "/hbbackground enable | disable | status | reload");
        return true;
    }

    private void requireConfigured(World world) {
        if (!getConfig().contains(path(world) + ".model")) {
            throw new IllegalArgumentException("먼저 /hbbackground set <model>을 실행하세요.");
        }
    }

    private void validateModelId(String model) {
        if (model.isBlank() || model.length() > 128 || !model.matches("[A-Za-z0-9_./:-]+")) {
            throw new IllegalArgumentException("유효하지 않은 모델 ID입니다.");
        }
    }

    private void reply(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.AQUA + "[HB3D] " + ChatColor.WHITE + message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("set", "position", "scale", "rotation", "enable", "disable", "status", "reload")
                    .stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }

        return List.of();
    }
}
