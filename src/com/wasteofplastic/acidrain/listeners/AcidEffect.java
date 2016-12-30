/*******************************************************************************
 * This file is part of ASkyBlock.
 *
 *     ASkyBlock is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ASkyBlock is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ASkyBlock.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package com.wasteofplastic.acidrain.listeners;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import com.wasteofplastic.acidrain.AcidRain;
import com.wasteofplastic.acidrain.Settings;
import com.wasteofplastic.acidrain.util.VaultHelper;

/**
 * Applies the acid effect to players
 * 
 * @author tastybento
 */
public class AcidEffect implements Listener {
    private final AcidRain plugin;
    private Set<UUID> burningPlayers = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private Set<UUID> wetPlayers = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    //private List<UUID> burningPlayers = new ArrayList<UUID>();
    private boolean isRaining = false;
    //private List<UUID> wetPlayers = new ArrayList<UUID>();

    public AcidEffect(final AcidRain pluginI) {
        plugin = pluginI;

        // This runnable continuously hurts the player even if they are not
        // moving but are in acid.
        new BukkitRunnable() {
            @Override
            public void run() {
                // Look through wet players
                Iterator<UUID> it = burningPlayers.iterator();
                while (it.hasNext()) {
                    Player player = plugin.getServer().getPlayer(it.next());
                    // Check if the player is still online
                    if (player == null) {
                        // Nope
                        it.remove();
                        continue;
                    }
                    if (player.isDead()) {
                        it.remove();
                        continue;
                    } else if ((player.getLocation().getBlock().isLiquid() || player.getLocation().getBlock().getRelative(BlockFace.UP).isLiquid())
                            && AcidRain.getAcidRainWorlds().contains(player.getWorld())) {
                        damagePlayer(player);
                    } else {
                        it.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        // Run a recurring task to hurt players when rain is falling
        new BukkitRunnable() {
            @Override
            public void run() {
                // Check if it is still raining 
                if (!isRaining || Settings.rainDamage <= 0D) {
                    wetPlayers.clear();
                    return;
                }
                // Look through wet players
                Iterator<UUID> it = wetPlayers.iterator();
                while (it.hasNext()) {
                    Player player = plugin.getServer().getPlayer(it.next());
                    // Check if the player is still online
                    if (player == null) {
                        // Nope
                        it.remove();
                        continue;
                    }
                    // If player is dead or not in the world remove them
                    if (player.isDead() || !AcidRain.getAcidRainWorlds().contains(player.getWorld()) || !player.getGameMode().equals(GameMode.SURVIVAL)) {
                        //plugin.getLogger().info("DEBUG: Player is dead, not in survival or it has stopped raining");
                        it.remove();
                        continue;
                    } else {
                        // Check if they have drunk a potion
                        // Check if player has an active water
                        // potion or not
                        Collection<PotionEffect> activePotions = player.getActivePotionEffects();
                        for (PotionEffect s : activePotions) {
                            // plugin.getLogger().info("Potion is : "
                            // +
                            // s.getType().toString());
                            if (s.getType().equals(PotionEffectType.WATER_BREATHING)) {
                                // Safe!
                                it.remove();
                                continue;
                            }
                        }
                        // Check if they are still in rain
                        // Check if all air above player
                        boolean safe = false;
                        for (int y = player.getLocation().getBlockY() + 2; y < player.getWorld().getMaxHeight(); y++) {
                            if (!player.getWorld().getBlockAt(player.getLocation().getBlockX(), y, player.getLocation().getBlockZ())
                                    .getType().equals(Material.AIR)) {
                                // Safe!
                                it.remove();
                                //plugin.getLogger().info("DEBUG: Moved out of the rain");
                                safe = true;
                                break;
                            }
                        }
                        if (safe) continue;
                        // Apply damage if there is any - no potion
                        // damage for rain
                        //plugin.getLogger().info("DEBUG: Player is in the rain");
                        double health = player.getHealth() - (Settings.rainDamage - Settings.rainDamage * getDamageReduced(player));
                        if (health < 0D) {
                            health = 0D;
                        } else if (health > player.getMaxHealth()) {
                            health = player.getMaxHealth();
                        }
                        player.setHealth(health);
                        if (plugin.getServer().getVersion().contains("(MC: 1.8") || plugin.getServer().getVersion().contains("(MC: 1.7")) {
                            player.getWorld().playSound(player.getLocation(), Sound.valueOf("FIZZ"), 3F, 3F);
                        } else {
                            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 3F, 3F);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Hurts a player
     * @param player
     */
    private void damagePlayer(Player player) {
        // Not hurt creative mode players
        if (!player.getGameMode().equals(GameMode.SURVIVAL)) {
            burningPlayers.remove(player.getUniqueId());
            wetPlayers.remove(player.getUniqueId());
            return;
        }
        if (!Settings.acidDamageType.isEmpty()) {
            for (PotionEffectType t : Settings.acidDamageType) {
                if (t.equals(PotionEffectType.BLINDNESS) || t.equals(PotionEffectType.CONFUSION) || t.equals(PotionEffectType.HUNGER)
                        || t.equals(PotionEffectType.SLOW) || t.equals(PotionEffectType.SLOW_DIGGING) || t.equals(PotionEffectType.WEAKNESS)) {
                    player.addPotionEffect(new PotionEffect(t, 600, 1));
                } else {
                    // Poison
                    player.addPotionEffect(new PotionEffect(t, 200, 1));
                }
            }
        }
        // double health = player.getHealth();
        // Apply damage if there is any
        if (Settings.acidDamage > 0D) {
            double health = player.getHealth() - (Settings.acidDamage - Settings.acidDamage * getDamageReduced(player));
            if (health < 0D) {
                health = 0D;
            }
            player.setHealth(health);
            if (plugin.getServer().getVersion().contains("(MC: 1.8") || plugin.getServer().getVersion().contains("(MC: 1.7")) {
                player.getWorld().playSound(player.getLocation(), Sound.valueOf("FIZZ"), 3F, 3F);
            } else {
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 3F, 3F);
            }
        }

    }

    /**
     * Death is the only escape
     * @param e
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (e.getEntity() instanceof Player) {
            burningPlayers.remove(e.getEntity().getUniqueId());
            wetPlayers.remove(e.getEntity().getUniqueId());
        }
    }

    /**
     * Check movement
     * @param e
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        // Fast return if acid isn't being used
        if (Settings.rainDamage == 0 && Settings.acidDamage == 0) {
            burningPlayers.clear();
            wetPlayers.clear();
            return;
        }
        final Player player = e.getPlayer();
        // Fast checks
        if (player.isDead()) {
            return;
        }
        // Check that they are in the ASkyBlock world
        if (!AcidRain.getAcidRainWorlds().contains(player.getWorld())) {
            return;
        }        
        // Return if players are immune
        if (player.isOp()) {
            if (!Settings.damageOps) {
                return;
            }
        } else if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "mod.noburn") || VaultHelper.checkPerm(player, Settings.PERMPREFIX + "admin.noburn")) {
            return;
        }

        if (player.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }

        // Slow checks
        final Location playerLoc = player.getLocation();
        final Block block = playerLoc.getBlock();
        final Block head = block.getRelative(BlockFace.UP);

        // Check for acid rain
        if (Settings.rainDamage > 0D && isRaining) {
            // Only check if they are in a non-dry biome
            Biome biome = playerLoc.getBlock().getBiome();
            if (biome.name().contains("DESERT") || biome.name().contains("SAVANNA") 
                    || biome.name().contains("MESA") || biome.name().contains("HELL")) {
                // Remove from wet players because they are in a dry biome
                wetPlayers.remove(player.getUniqueId());
            } else {
                //plugin.getLogger().info("Rain damage = " + Settings.rainDamage);
                boolean hitByRain = true;
                // Check if all air above player
                for (int y = playerLoc.getBlockY() + 2; y < playerLoc.getWorld().getMaxHeight(); y++) {
                    if (!playerLoc.getWorld().getBlockAt(playerLoc.getBlockX(), y, playerLoc.getBlockZ()).getType().equals(Material.AIR)) {
                        hitByRain = false;
                        break;
                    }
                }
                if (!hitByRain) {
                    // plugin.getLogger().info("DEBUG: not hit by rain");
                    wetPlayers.remove(player);
                } else {
                    // plugin.getLogger().info("DEBUG: hit by rain");
                    // Check if player has an active water potion or not
                    boolean acidPotion = false;
                    Collection<PotionEffect> activePotions = player.getActivePotionEffects();
                    for (PotionEffect s : activePotions) {
                        // plugin.getLogger().info("Potion is : " +
                        // s.getType().toString());
                        if (s.getType().equals(PotionEffectType.WATER_BREATHING)) {
                            // Safe!
                            wetPlayers.remove(player.getUniqueId());
                            acidPotion = true;
                            break;
                        }
                    }
                    if (acidPotion) {
                        // plugin.getLogger().info("DEBUG: Acid potion active");
                        wetPlayers.remove(player);
                    } else {
                        wetPlayers.add(player.getUniqueId());
                    }
                }
            }
        } else {
            // Not raining
            wetPlayers.clear();
        }
        // If they are not in liquid, then return
        if (!block.isLiquid() && !head.isLiquid()) {
            return;
        }
        // If they are already burning in acid then return
        if (burningPlayers.contains(player.getUniqueId())) {
            return;
        }
        // plugin.getLogger().info("DEBUG: no acid water is false");
        // Check if they are in water
        if (block.getType().equals(Material.STATIONARY_WATER) || block.getType().equals(Material.WATER)
                || head.getType().equals(Material.STATIONARY_WATER) || head.getType().equals(Material.WATER)) {
            // Check if player is in a boat
            Entity playersVehicle = player.getVehicle();
            if (playersVehicle != null) {
                // They are in a Vehicle
                if (playersVehicle.getType().toString().contains("BOAT")) {
                    // I'M ON A BOAT! I'M ON A BOAT! A %^&&* BOAT!
                    return;
                }
            }
            // Check if player has an active water potion or not
            Collection<PotionEffect> activePotions = player.getActivePotionEffects();
            for (PotionEffect s : activePotions) {
                // plugin.getLogger().info("Potion is : " +
                // s.getType().toString());
                if (s.getType().equals(PotionEffectType.WATER_BREATHING)) {
                    // Safe!
                    //plugin.getLogger().info("DEBUG: Water breathing potion protection!");
                    return;
                }
            }
            // ACID!
            //plugin.getLogger().info("DEBUG: Acid!");
            // Put the player into the acid list
            burningPlayers.add(player.getUniqueId());
        }
    }

    /**
     * @param player
     * @return A double between 0.0 and 0.80 that reflects how much armor the
     *         player has on. The higher the value, the more protection they
     *         have.
     */
    static public double getDamageReduced(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack boots = inv.getBoots();
        ItemStack helmet = inv.getHelmet();
        ItemStack chest = inv.getChestplate();
        ItemStack pants = inv.getLeggings();
        double red = 0.0;
        if (helmet != null) {
            if (helmet.getType() == Material.LEATHER_HELMET)
                red = red + 0.04;
            else if (helmet.getType() == Material.GOLD_HELMET)
                red = red + 0.08;
            else if (helmet.getType() == Material.CHAINMAIL_HELMET)
                red = red + 0.08;
            else if (helmet.getType() == Material.IRON_HELMET)
                red = red + 0.08;
            else if (helmet.getType() == Material.DIAMOND_HELMET)
                red = red + 0.12;
        }
        if (boots != null) {
            if (boots.getType() == Material.LEATHER_BOOTS)
                red = red + 0.04;
            else if (boots.getType() == Material.GOLD_BOOTS)
                red = red + 0.04;
            else if (boots.getType() == Material.CHAINMAIL_BOOTS)
                red = red + 0.04;
            else if (boots.getType() == Material.IRON_BOOTS)
                red = red + 0.08;
            else if (boots.getType() == Material.DIAMOND_BOOTS)
                red = red + 0.12;
        }
        // Pants
        if (pants != null) {
            if (pants.getType() == Material.LEATHER_LEGGINGS)
                red = red + 0.08;
            else if (pants.getType() == Material.GOLD_LEGGINGS)
                red = red + 0.12;
            else if (pants.getType() == Material.CHAINMAIL_LEGGINGS)
                red = red + 0.16;
            else if (pants.getType() == Material.IRON_LEGGINGS)
                red = red + 0.20;
            else if (pants.getType() == Material.DIAMOND_LEGGINGS)
                red = red + 0.24;
        }
        // Chest plate
        if (chest != null) {
            if (chest.getType() == Material.LEATHER_CHESTPLATE)
                red = red + 0.12;
            else if (chest.getType() == Material.GOLD_CHESTPLATE)
                red = red + 0.20;
            else if (chest.getType() == Material.CHAINMAIL_CHESTPLATE)
                red = red + 0.20;
            else if (chest.getType() == Material.IRON_CHESTPLATE)
                red = red + 0.24;
            else if (chest.getType() == Material.DIAMOND_CHESTPLATE)
                red = red + 0.32;
        }
        return red;
    }

    /**
     * Tracks weather changes and acid rain
     * 
     * @param e
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWeatherChange(final WeatherChangeEvent e) {
        // Check that they are in the ASkyBlock world
        // plugin.getLogger().info("weather change noted");
        if (!AcidRain.getAcidRainWorlds().contains(e.getWorld())) {
            return;
        }
        this.isRaining = e.toWeatherState();
        // plugin.getLogger().info("is raining = " + isRaining);
    }

}