package com.y4vyq.partnerplugin;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class PartnerPlugin extends JavaPlugin implements Listener {

    // 粒子效果生成器接口
    public interface ParticleGenerator {
        void generateEffect(Location location);
    }

    // 元数据键值枚举
    private enum MetadataKey {
        PARTNER("MyPartner"),
        OWNER("PartnerOwner"),
        HIDDEN("PartnerHidden");

        private final String value;

        MetadataKey(String value) {
            this.value = value;
        }

        public String getKey() {
            return value;
        }
    }

    // 伙伴管理集合
    private final Map<UUID, LivingEntity> activePartners = new HashMap<>();
    private final Map<UUID, BukkitRunnable> removalTasks = new HashMap<>();
    private final Map<UUID, BukkitRunnable> teleportCooldowns = new HashMap<>();
    
    // 配置相关
    private FileConfiguration config;
    private FileConfiguration helpConfig;
    
    // 粒子效果生成器
    private ParticleGenerator particleGenerator;
    
    // 默认配置值
    private static final String DEFAULT_ENTITY_TYPE = "FOX";
    private static final int DEFAULT_DURATION = 300;
    private static final String DEFAULT_SOUND = "ENTITY_FOX_AMBIENT";

    @Override
    public void onEnable() {
        getLogger().info(ChatColor.translateAlternateColorCodes('&', "&b[伙伴]&f 伙伴插件已启用！"));
        saveDefaultConfig();
        reloadConfigs();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // 初始化粒子生成器
        initParticleGenerator();
        
        // 定时保存隐藏伙伴
        new BukkitRunnable() {
            @Override
            public void run() {
                saveHiddenPartners();
            }
        }.runTaskTimer(this, 20 * 60 * 5, 20 * 60 * 5);
    }

    @Override
    public void onDisable() {
        saveHiddenPartners();
        cleanupAllPartners();
    }
    
    /**
     * 初始化粒子生成器
     */
    private void initParticleGenerator() {
        String generatorType = config.getString("particle-effect.generator-type", "default").toLowerCase();
        
        switch (generatorType) {
            case "custom":
                particleGenerator = new ConfigurableParticleGenerator(config);
                getLogger().info("使用可配置粒子效果生成器");
                break;
            case "spiral":
                particleGenerator = new SpiralParticleGenerator();
                getLogger().info("使用螺旋粒子效果生成器");
                break;
            case "circle":
                particleGenerator = new CircleParticleGenerator();
                getLogger().info("使用圆形粒子效果生成器");
                break;
            case "default":
            default:
                particleGenerator = new DefaultParticleGenerator();
                getLogger().info("使用默认粒子效果生成器");
        }
    }
    
    /**
     * 重新加载配置文件
     */
    private void reloadConfigs() {
        reloadConfig();
        config = getConfig();
        
        File helpFile = new File(getDataFolder(), "help.yml");
        if (!helpFile.exists()) {
            saveResource("help.yml", false);
        }
        helpConfig = YamlConfiguration.loadConfiguration(helpFile);
        
        // 重新初始化粒子生成器
        initParticleGenerator();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sendColoredMessage(sender, "&b[伙伴]&f 只有玩家可以使用此命令！");
            return true;
        }

        Player player = (Player) sender;
        
        if (args.length == 0) {
            return handleSummonCommand(player);
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sendHelp(player);
                return true;
            case "reload":
                return handleReloadCommand(player);
            case "hide":
                return hidePartner(player);
            case "show":
                return showPartner(player);
            case "particle":
                return handleParticleCommand(player, args);
            default:
                sendColoredMessage(player, "&b[伙伴]&f 未知命令！输入 /partner help 查看帮助");
                return true;
        }
    }
    
    /**
     * 处理粒子效果命令
     */
    private boolean handleParticleCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendColoredMessage(player, "&b[伙伴]&f 用法: /partner particle <default|spiral|circle|custom>");
            return false;
        }
        
        String type = args[1].toLowerCase();
        switch (type) {
            case "default":
                particleGenerator = new DefaultParticleGenerator();
                sendColoredMessage(player, "&b[伙伴]&f 已切换至默认粒子效果");
                break;
            case "spiral":
                particleGenerator = new SpiralParticleGenerator();
                sendColoredMessage(player, "&b[伙伴]&f 已切换至螺旋粒子效果");
                break;
            case "circle":
                particleGenerator = new CircleParticleGenerator();
                sendColoredMessage(player, "&b[伙伴]&f 已切换至圆形粒子效果");
                break;
            case "custom":
                particleGenerator = new ConfigurableParticleGenerator(config);
                sendColoredMessage(player, "&b[伙伴]&f 已切换至配置文件粒子效果");
                break;
            default:
                sendColoredMessage(player, "&b[伙伴]&f 未知粒子效果类型！可用类型: default, spiral, circle, custom");
                return false;
        }
        
        return true;
    }
    
    /**
     * 处理重载命令
     */
    private boolean handleReloadCommand(Player player) {
        if (player.hasPermission("partner.reload")) {
            reloadConfigs();
            sendColoredMessage(player, "&b[伙伴]&f 配置已重载！");
            return true;
        }
        sendColoredMessage(player, "&b[伙伴]&f 你没有权限执行此命令！");
        return false;
    }
    
    /**
     * 发送彩色消息
     */
    private void sendColoredMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
    
    /**
     * 处理召唤命令
     */
    private boolean handleSummonCommand(Player player) {
        if (!canSummonPartner(player)) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        LivingEntity existingPartner = activePartners.get(playerId);
        
        // 已有伙伴时隐藏
        if (existingPartner != null && existingPartner.isValid() && !existingPartner.isDead()) {
            return hidePartner(player);
        }

        // 清理无效伙伴
        if (existingPartner != null) {
            cleanupPartner(playerId);
            sendColoredMessage(player, "&b[伙伴]&f 正在重新召唤伙伴...");
        }

        return summonPartner(player);
    }
    
    /**
     * 隐藏伙伴
     */
    private boolean hidePartner(Player player) {
        UUID playerId = player.getUniqueId();
        LivingEntity partner = activePartners.get(playerId);
        
        if (partner == null) {
            sendColoredMessage(player, "&b[伙伴]&f 你还没有伙伴可以隐藏！");
            return false;
        }
        
        // 设置隐藏元数据
        partner.setMetadata(MetadataKey.HIDDEN.getKey(), new FixedMetadataValue(this, true));
        partner.remove();
        sendColoredMessage(player, "&b[伙伴]&f 伙伴已隐藏！输入 /partner show 重新召唤");
        return true;
    }
    
    /**
     * 显示伙伴
     */
    private boolean showPartner(Player player) {
        UUID playerId = player.getUniqueId();
        LivingEntity partner = activePartners.get(playerId);
        
        if (partner == null) {
            sendColoredMessage(player, "&b[伙伴]&f 你还没有伙伴！");
            return false;
        }
        
        if (!partner.hasMetadata(MetadataKey.HIDDEN.getKey())) {
            sendColoredMessage(player, "&b[伙伴]&f 你的伙伴已经显示出来了！");
            return false;
        }

        respawnPartner(player, partner);
        return true;
    }
    
    /**
     * 重新生成伙伴
     */
    private void respawnPartner(Player player, LivingEntity oldPartner) {
        // 清除隐藏状态
        oldPartner.removeMetadata(MetadataKey.HIDDEN.getKey(), this);
        
        // 计算生成位置
        Location spawnLoc = calculateSpawnLocation(player);
        
        // 创建新实体
        LivingEntity newPartner = (LivingEntity) player.getWorld().spawnEntity(spawnLoc, oldPartner.getType());
        newPartner.setCustomName(oldPartner.getCustomName());
        newPartner.setCustomNameVisible(oldPartner.isCustomNameVisible());
        newPartner.setInvulnerable(true);
        
        // 复制元数据
        copyMetadata(oldPartner, newPartner);
        handleSpecialEntities(player, newPartner);
        
        // 更新伙伴映射
        activePartners.put(player.getUniqueId(), newPartner);
        
        // 播放效果
        playSummonEffect(spawnLoc);
        sendColoredMessage(player, "&b[伙伴]&f 伙伴已重新出现！");
    }
    
    /**
     * 计算生成位置
     */
    private Location calculateSpawnLocation(Player player) {
        Location loc = player.getLocation();
        Location spawnLoc = loc.add(loc.getDirection().multiply(2));
        spawnLoc.add(0, 0.5, 0);
        return spawnLoc;
    }
    
    /**
     * 复制元数据
     */
    private void copyMetadata(Entity source, Entity target) {
        for (MetadataKey key : MetadataKey.values()) {
            if (source.hasMetadata(key.getKey())) {
                for (MetadataValue meta : source.getMetadata(key.getKey())) {
                    target.setMetadata(key.getKey(), new FixedMetadataValue(this, meta.value()));
                }
            }
        }
    }
    
    /**
     * 发送帮助信息
     */
    private void sendHelp(CommandSender sender) {
        int duration = config.getInt("partner.duration", DEFAULT_DURATION);
        String entityType = config.getString("partner.entity-type", DEFAULT_ENTITY_TYPE);
        
        List<String> helpLines = helpConfig.getStringList("help-messages").stream()
            .map(line -> line.replace("{duration}", String.valueOf(duration)))
            .map(line -> line.replace("{entity-type}", entityType))
            .map(line -> ChatColor.translateAlternateColorCodes('&', line))
            .collect(Collectors.toList());
        
        helpLines.forEach(sender::sendMessage);
    }

    /**
     * 检查是否可以召唤伙伴
     */
    private boolean canSummonPartner(Player player) {
        // 旁观模式检查
        if (player.getGameMode() == GameMode.SPECTATOR) {
            sendColoredMessage(player, "&b[伙伴]&f 旁观模式下不能召唤伙伴！");
            return false;
        }
        
        // 水中检查
        if (player.isInWater() || player.getLocation().getBlock().isLiquid()) {
            sendColoredMessage(player, "&b[伙伴]&f 在水中不能召唤伙伴！");
            return false;
        }
        
        // 载具检查
        if (player.isInsideVehicle()) {
            sendColoredMessage(player, "&b[伙伴]&f 在坐骑或载具上不能召唤伙伴！");
            return false;
        }
        
        // 冷却检查
        if (teleportCooldowns.containsKey(player.getUniqueId())) {
            sendColoredMessage(player, "&b[伙伴]&f 你的伙伴正在冷却中，请稍后再试！");
            return false;
        }
        
        return true;
    }

    /**
     * 召唤伙伴实体
     */
    private boolean summonPartner(Player player) {
        // 获取实体类型配置
        String entityTypeName = config.getString("partner.entity-type", DEFAULT_ENTITY_TYPE);
        EntityType entityType = parseEntityType(entityTypeName);
        
        // 获取配置参数
        int duration = config.getInt("partner.duration", DEFAULT_DURATION);
        String customName = ChatColor.translateAlternateColorCodes('&', 
                config.getString("partner.name", "&6&l我的伙伴"));
        
        // 计算生成位置
        Location spawnLoc = calculateSpawnLocation(player);
        
        // 播放生成效果
        playSummonEffect(spawnLoc);
        
        // 生成实体
        LivingEntity partner = (LivingEntity) player.getWorld().spawnEntity(spawnLoc, entityType);
        partner.setCustomName(customName);
        partner.setCustomNameVisible(config.getBoolean("partner.show-name", true));
        partner.setInvulnerable(true);
        
        // 设置元数据
        partner.setMetadata(MetadataKey.PARTNER.getKey(), new FixedMetadataValue(this, true));
        partner.setMetadata(MetadataKey.OWNER.getKey(), 
                new FixedMetadataValue(this, player.getUniqueId().toString()));
        
        // 特殊实体处理
        handleSpecialEntities(player, partner);
        
        // 应用属性
        applyEntityAttributes(partner);
        
        // 更新集合
        activePartners.put(player.getUniqueId(), partner);
        startRemovalTimer(player.getUniqueId(), duration);
        
        // 播放音效
        playSummonSound(player);
        
        sendColoredMessage(player, "&b[伙伴]&f 伙伴已出现！持续 " + duration + "秒");
        return true;
    }
    
    /**
     * 解析实体类型
     */
    private EntityType parseEntityType(String typeName) {
        try {
            EntityType type = EntityType.valueOf(typeName.toUpperCase());
            if (type.isAlive()) return type;
        } catch (IllegalArgumentException ignored) {}
        
        getLogger().warning("无效的实体类型: " + typeName + "，使用默认值 " + DEFAULT_ENTITY_TYPE);
        return EntityType.valueOf(DEFAULT_ENTITY_TYPE);
    }
    
    /**
     * 播放召唤音效
     */
    private void playSummonSound(Player player) {
        String soundName = config.getString("summon-effect.sound", DEFAULT_SOUND);
        float volume = (float) config.getDouble("summon-effect.volume", 1.0);
        float pitch = (float) config.getDouble("summon-effect.pitch", 1.0);
        
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            getLogger().warning("无效的音效: " + soundName);
        }
    }
    
    /**
     * 播放召唤粒子效果
     */
    private void playSummonEffect(Location location) {
        if (particleGenerator != null) {
            particleGenerator.generateEffect(location);
        } else {
            // 回退到默认粒子效果
            new DefaultParticleGenerator().generateEffect(location);
        }
    }
    
    /**
     * 处理特殊实体（可驯服动物等）
     */
    private void handleSpecialEntities(Player player, LivingEntity entity) {
        // 狐狸特殊处理
        if (entity instanceof Fox) {
            handleFoxEntity(player, (Fox) entity);
        } 
        // 可驯服动物处理（仅处理实现了Tameable接口的实体）
        else if (entity instanceof Tameable) {
            Tameable tameable = (Tameable) entity;
            tameable.setTamed(true);
            tameable.setOwner(player);
        }
    }
    
    /**
     * 处理狐狸实体（修复核心错误：移除setTamed和setOwner调用）
     */
    private void handleFoxEntity(Player player, Fox fox) {
        try {
            // 使用反射调用Fox类的addTrustedUUID方法（狐狸的信任机制）
            Method addTrusted = Fox.class.getMethod("addTrustedUUID", UUID.class);
            addTrusted.invoke(fox, player.getUniqueId());
            getLogger().info("成功将玩家 " + player.getName() + " 添加为狐狸的信任对象");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // 反射失败时的降级处理（不调用setTamed/setOwner，仅日志提示）
            getLogger().warning("无法设置狐狸的信任对象，可能是API版本不兼容: " + e.getMessage());
        }
    }
    
    /**
     * 应用实体属性
     */
    private void applyEntityAttributes(LivingEntity entity) {
        ConfigurationSection attributes = config.getConfigurationSection("entity-attributes");
        if (attributes == null) return;
        
        // 生命值
        if (attributes.isDouble("health")) {
            double health = attributes.getDouble("health");
            entity.setHealth(Math.min(health, entity.getMaxHealth()));
        }
        
        // AI控制
        if (attributes.isBoolean("ai")) {
            entity.setAI(attributes.getBoolean("ai"));
        }
        
        // 发光效果
        if (attributes.isBoolean("glowing")) {
            entity.setGlowing(attributes.getBoolean("glowing"));
        }
        
        // 静音
        if (attributes.isBoolean("silent")) {
            entity.setSilent(attributes.getBoolean("silent"));
        }
        
        // 年龄设置
        if (attributes.isBoolean("age") && entity instanceof Ageable) {
            Ageable ageable = (Ageable) entity;
            if (attributes.getBoolean("age")) {
                ageable.setAdult();
            } else {
                ageable.setBaby();
            }
        }
    }

    /**
     * 启动移除计时器
     */
    private void startRemovalTimer(UUID playerId, int duration) {
        // 取消现有任务
        if (removalTasks.containsKey(playerId)) {
            removalTasks.get(playerId).cancel();
        }
        
        BukkitRunnable removalTask = new BukkitRunnable() {
            @Override
            public void run() {
                LivingEntity partner = activePartners.remove(playerId);
                if (partner == null) return;
                
                // 播放消失效果
                Location loc = partner.getLocation();
                loc.getWorld().spawnParticle(Particle.CLOUD, loc, 30, 0.5, 0.5, 0.5, 0.1);
                
                // 播放音效
                try {
                    loc.getWorld().playSound(loc, Sound.valueOf("ENTITY_FOX_SLEEP"), 0.8f, 1.2f);
                } catch (IllegalArgumentException e) {
                    try {
                        loc.getWorld().playSound(loc, Sound.valueOf("ENTITY_CAT_PURR"), 0.8f, 1.2f);
                    } catch (IllegalArgumentException ignored) {}
                }
                
                // 移除实体
                if (!partner.isDead()) {
                    partner.remove();
                }
                
                removalTasks.remove(playerId);
            }
        };
        
        removalTask.runTaskLater(this, 20 * duration);
        removalTasks.put(playerId, removalTask);
    }
    
    /**
     * 清理伙伴
     */
    private void cleanupPartner(UUID playerId) {
        LivingEntity partner = activePartners.remove(playerId);
        if (partner != null && !partner.isDead()) {
            partner.remove();
        }
        
        BukkitRunnable task = removalTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * 清理所有伙伴
     */
    private void cleanupAllPartners() {
        // 清理实体
        activePartners.values().stream()
            .filter(partner -> partner != null && !partner.isDead())
            .forEach(Entity::remove);
        activePartners.clear();
        
        // 取消任务
        removalTasks.values().forEach(BukkitRunnable::cancel);
        removalTasks.clear();
        
        teleportCooldowns.values().forEach(BukkitRunnable::cancel);
        teleportCooldowns.clear();
    }
    
    /**
     * 保存隐藏伙伴状态
     */
    private void saveHiddenPartners() {
        long hiddenCount = activePartners.values().stream()
            .filter(partner -> partner != null && partner.hasMetadata(MetadataKey.HIDDEN.getKey()))
            .count();
            
        if (hiddenCount > 0) {
            getLogger().info(ChatColor.translateAlternateColorCodes('&', 
                "&b[伙伴]&f 保存了 " + hiddenCount + " 个隐藏伙伴"));
        }
    }
    
    // ================== 事件处理器 ================== //
    
    @EventHandler
    public void onPartnerDamage(EntityDamageEvent event) {
        if (event.getEntity().hasMetadata(MetadataKey.PARTNER.getKey())) {
            event.setCancelled(true);
            event.getEntity().setFireTicks(0);
        }
    }
    
    @EventHandler
    public void onPartnerDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!entity.hasMetadata(MetadataKey.PARTNER.getKey())) return;
        
        String ownerIdStr = null;
        try {
            ownerIdStr = entity.getMetadata(MetadataKey.OWNER.getKey()).get(0).asString();
            UUID ownerId = UUID.fromString(ownerIdStr);
            
            activePartners.remove(ownerId);
            
            BukkitRunnable task = removalTasks.remove(ownerId);
            if (task != null) task.cancel();
            
            Player player = Bukkit.getPlayer(ownerId);
            if (player != null) {
                sendColoredMessage(player, "&b[伙伴]&f 你的伙伴死亡了！");
            }
        } catch (IllegalArgumentException e) {
            getLogger().warning("无效的UUID: " + (ownerIdStr != null ? ownerIdStr : "null"));
        }
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        LivingEntity partner = activePartners.get(playerId);
        if (partner == null) return;
        
        // 检查伙伴状态
        if (partner.isDead() || !partner.isValid()) {
            activePartners.remove(playerId);
            return;
        }
        
        // 跳过隐藏伙伴
        if (partner.hasMetadata(MetadataKey.HIDDEN.getKey())) return;
        
        Location to = event.getTo();
        if (to == null) return;
        
        // 跨世界传送
        if (!event.getFrom().getWorld().equals(to.getWorld())) {
            handleCrossWorldTeleport(player, partner, to);
        } 
        // 同世界传送
        else {
            handleSameWorldTeleport(player, partner, to);
        }
    }
    
    /**
     * 处理跨世界传送
     */
    private void handleCrossWorldTeleport(Player player, LivingEntity partner, Location to) {
        partner.remove();
        
        Location newLoc = to.clone().add(player.getLocation().getDirection().multiply(2));
        newLoc.add(0, 0.5, 0);
        
        LivingEntity newPartner = (LivingEntity) to.getWorld().spawnEntity(newLoc, partner.getType());
        newPartner.setCustomName(partner.getCustomName());
        newPartner.setCustomNameVisible(partner.isCustomNameVisible());
        newPartner.setInvulnerable(true);
        
        copyMetadata(partner, newPartner);
        handleSpecialEntities(player, newPartner);
        
        activePartners.put(player.getUniqueId(), newPartner);
        sendColoredMessage(player, "&b[伙伴]&f 伙伴已传送到新世界！");
    }
    
    /**
     * 处理同世界传送
     */
    private void handleSameWorldTeleport(Player player, LivingEntity partner, Location to) {
        if (teleportCooldowns.containsKey(player.getUniqueId())) return;
        
        Location teleportLoc = to.clone().add(player.getLocation().getDirection().multiply(2));
        teleportLoc.add(0, 0.5, 0);
        partner.teleport(teleportLoc);
        
        // 设置传送冷却
        BukkitRunnable cooldown = new BukkitRunnable() {
            @Override
            public void run() {
                teleportCooldowns.remove(player.getUniqueId());
            }
        };
        cooldown.runTaskLater(this, 20);
        teleportCooldowns.put(player.getUniqueId(), cooldown);
    }
    
    // ================== 粒子效果生成器实现 ================== //
    
    /**
     * 默认粒子效果生成器（硬编码实现）
     */
    private static class DefaultParticleGenerator implements ParticleGenerator {
        @Override
        public void generateEffect(Location location) {
            World world = location.getWorld();
            if (world == null) return;
            
            Particle particle = Particle.FLAME;
            int count = 100;
            double radius = 1.5;
            int duration = 20;
            
            new BukkitRunnable() {
                int ticks = 0;
                
                @Override
                public void run() {
                    if (ticks >= duration) {
                        cancel();
                        return;
                    }
                    
                    double progress = (double) ticks / duration;
                    double height = Math.sin(progress * Math.PI) * 0.5;
                    
                    for (int i = 0; i < count; i++) {
                        double angle = 2 * Math.PI * i / count;
                        double x = radius * Math.cos(angle);
                        double z = radius * Math.sin(angle);
                        
                        world.spawnParticle(
                            particle, 
                            location.getX() + x, 
                            location.getY() + height, 
                            location.getZ() + z, 
                            1
                        );
                    }
                    
                    ticks++;
                }
            }.runTaskTimer(PartnerPlugin.getPlugin(PartnerPlugin.class), 0, 1);
        }
    }
    
    /**
     * 螺旋粒子效果生成器（硬编码实现）
     */
    private static class SpiralParticleGenerator implements ParticleGenerator {
        @Override
        public void generateEffect(Location location) {
            World world = location.getWorld();
            if (world == null) return;
            
            Particle particle = Particle.FLAME;
            int points = 100;
            int duration = 100;
            double radius = 1.0;
            double height = 2.0;
            
            new BukkitRunnable() {
                int ticks = 0;
                
                @Override
                public void run() {
                    if (ticks >= duration) {
                        cancel();
                        return;
                    }
                    
                    for (int i = 0; i < points; i++) {
                        double progress = (double) i / points;
                        double angle = 2 * Math.PI * (ticks / 10.0 + progress);
                        double x = radius * Math.cos(angle);
                        double z = radius * Math.sin(angle);
                        double y = height * (progress - 0.5);
                        
                        world.spawnParticle(
                            particle, 
                            location.getX() + x, 
                            location.getY() + y, 
                            location.getZ() + z, 
                            1
                        );
                    }
                    
                    ticks++;
                }
            }.runTaskTimer(PartnerPlugin.getPlugin(PartnerPlugin.class), 0, 1);
        }
    }
    
    /**
     * 圆形粒子效果生成器（硬编码实现）
     */
    private static class CircleParticleGenerator implements ParticleGenerator {
        @Override
        public void generateEffect(Location location) {
            World world = location.getWorld();
            if (world == null) return;
            
            Particle particle = Particle.FLAME;
            int count = 50;
            double radius = 1.5;
            int rings = 5;
            int duration = 20;
            
            new BukkitRunnable() {
                int ticks = 0;
                
                @Override
                public void run() {
                    if (ticks >= duration) {
                        cancel();
                        return;
                    }
                    
                    for (int r = 0; r < rings; r++) {
                        double height = (double) r / rings;
                        for (int i = 0; i < count; i++) {
                            double angle = 2 * Math.PI * i / count;
                            double x = radius * Math.cos(angle);
                            double z = radius * Math.sin(angle);
                            
                            world.spawnParticle(
                                particle, 
                                location.getX() + x, 
                                location.getY() + height, 
                                location.getZ() + z, 
                                1
                            );
                        }
                    }
                    
                    ticks++;
                }
            }.runTaskTimer(PartnerPlugin.getPlugin(PartnerPlugin.class), 0, 1);
        }
    }
    
    /**
     * 可配置粒子效果生成器
     */
    private static class ConfigurableParticleGenerator implements ParticleGenerator {
        private final FileConfiguration config;
        
        public ConfigurableParticleGenerator(FileConfiguration config) {
            this.config = config;
        }
        
        @Override
        public void generateEffect(Location location) {
            ConfigurationSection effectConfig = config.getConfigurationSection("particle-effect");
            if (effectConfig == null) return;
            
            final String particleName = effectConfig.getString("particle", "FLAME");
            final int count = effectConfig.getInt("count", 100);
            final double radius = effectConfig.getDouble("radius", 1.5);
            final int duration = effectConfig.getInt("duration", 20);
            final double heightMultiplier = effectConfig.getDouble("height-multiplier", 0.5);
            final int pointsPerTick = effectConfig.getInt("points-per-tick", 10);
            
            Particle particle;
            try {
                particle = Particle.valueOf(particleName);
            } catch (IllegalArgumentException e) {
                particle = Particle.FLAME;
            }
            
            final Particle finalParticle = particle;
            World world = location.getWorld();
            if (world == null) return;
            
            new BukkitRunnable() {
                int ticks = 0;
                int pointsGenerated = 0;
                
                @Override
                public void run() {
                    if (ticks >= duration) {
                        cancel();
                        return;
                    }
                    
                    // 每tick生成指定数量的粒子
                    for (int i = 0; i < pointsPerTick; i++) {
                        if (pointsGenerated >= count) {
                            pointsGenerated = 0;
                            break;
                        }
                        
                        double progress = (double) pointsGenerated / count;
                        double angle = 2 * Math.PI * progress;
                        double x = radius * Math.cos(angle);
                        double z = radius * Math.sin(angle);
                        double height = Math.sin(progress * Math.PI) * heightMultiplier;
                        
                        world.spawnParticle(
                            finalParticle, 
                            location.getX() + x, 
                            location.getY() + height, 
                            location.getZ() + z, 
                            1
                        );
                        
                        pointsGenerated++;
                    }
                    
                    ticks++;
                }
            }.runTaskTimer(PartnerPlugin.getPlugin(PartnerPlugin.class), 0, 1);
        }
    }
}
