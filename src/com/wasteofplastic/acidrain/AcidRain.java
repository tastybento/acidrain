/*******************************************************************************
 * This file is part of AcidRain.
 *
 *     AcidRain is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     AcidRain is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with AcidRain.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package com.wasteofplastic.acidrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Monster;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import com.wasteofplastic.acidrain.listeners.AcidEffect;
import com.wasteofplastic.acidrain.listeners.LavaCheck;
import com.wasteofplastic.acidrain.util.VaultHelper;

/**
 * @author tastybento
 *         AcidRain - acid water and rain
 */
public class AcidRain extends JavaPlugin {
    // This plugin
    private static AcidRain plugin;
    // The AcidRain world
    private static Set<World> AcidRainWorlds;
    private LavaCheck lavaListener;

    public static Set<World> getAcidRainWorlds() {
        return AcidRainWorlds;
    }

    /**
     * @return AcidRain object instance
     */
    public static AcidRain getPlugin() {
        return plugin;
    }

    /*
     * (non-Javadoc)
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {
    }

    /*
     * (non-Javadoc)
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
        // instance of this plugin
        plugin = this;
        saveDefaultConfig();
        // Load all the configuration of the plugin and localization strings
        loadPluginConfig();
        if (!VaultHelper.setupPermissions()) {
            getLogger().severe("Cannot link with Vault for permissions! Disabling plugin!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getAcidRainWorlds();
        // Register events
        registerEvents();
        // This part will kill monsters if they fall into the water
        // because it
        // is acid
        if (Settings.mobAcidDamage > 0D || Settings.animalAcidDamage > 0D) {
            getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
                @Override
                public void run() {
                    for (World world : AcidRainWorlds) {
                        List<Entity> entList = world.getEntities();
                        for (Entity current : entList) {
                            if (plugin.isOnePointEight() && current instanceof Guardian) {
                                // Guardians are immune to acid too
                                continue;
                            }
                            if ((current instanceof Monster) && Settings.mobAcidDamage > 0D) {
                                if ((current.getLocation().getBlock().getType() == Material.WATER)
                                        || (current.getLocation().getBlock().getType() == Material.STATIONARY_WATER)) {
                                    ((Monster) current).damage(Settings.mobAcidDamage);
                                    // getLogger().info("Killing monster");
                                }
                            } else if ((current instanceof Animals) && Settings.animalAcidDamage > 0D) {
                                if ((current.getLocation().getBlock().getType() == Material.WATER)
                                        || (current.getLocation().getBlock().getType() == Material.STATIONARY_WATER)) {
                                    if (!current.getType().equals(EntityType.CHICKEN)) {
                                        ((Animals) current).damage(Settings.animalAcidDamage);
                                    } else if (Settings.damageChickens) {
                                        ((Animals) current).damage(Settings.animalAcidDamage);
                                    }
                                    // getLogger().info("Killing animal");
                                }
                            }
                        }
                    }
                }
            }, 0L, 20L);
        }
    }


    /**
     * Loads the various settings from the config.yml file into the plugin
     */
    public boolean loadPluginConfig() {
        // getLogger().info("*********************************************");
        try {
            getConfig();
        } catch (final Exception e) {
            e.printStackTrace();
        }
        // Settings from config.yml
        Settings.worldNames = getConfig().getStringList("general.worldNames");
        AcidRainWorlds = new HashSet<World>();
        for (String worldName : Settings.worldNames) {
            World world = Bukkit.getServer().getWorld(worldName);
            if (world == null) {
                getLogger().severe("World name '" + worldName + "' is unknown - skipping...");
            } else {
                AcidRainWorlds.add(world);
            }
        }
        Settings.acidDamage = getConfig().getDouble("general.aciddamage", 5D);
        if (Settings.acidDamage > 100D) {
            Settings.acidDamage = 100D;
        } else if (Settings.acidDamage < 0D) {
            Settings.acidDamage = 0D;
        }
        Settings.mobAcidDamage = getConfig().getDouble("general.mobaciddamage", 10D);
        if (Settings.mobAcidDamage > 100D) {
            Settings.mobAcidDamage = 100D;
        } else if (Settings.mobAcidDamage < 0D) {
            Settings.mobAcidDamage = 0D;
        }
        Settings.rainDamage = getConfig().getDouble("general.raindamage", 0.5D);
        if (Settings.rainDamage > 100D) {
            Settings.rainDamage = 100D;
        } else if (Settings.rainDamage < 0D) {
            Settings.rainDamage = 0D;
        }
        Settings.animalAcidDamage = getConfig().getDouble("general.animaldamage", 0D);
        if (Settings.animalAcidDamage > 100D) {
            Settings.animalAcidDamage = 100D;
        } else if (Settings.animalAcidDamage < 0D) {
            Settings.animalAcidDamage = 0D;
        }
        Settings.damageChickens = getConfig().getBoolean("general.damagechickens", false);
        // Damage Type
        List<String> acidDamageType = getConfig().getStringList("general.damagetype");
        Settings.acidDamageType = new ArrayList<PotionEffectType>();
        if (acidDamageType != null) {
            for (String effect : acidDamageType) {
                PotionEffectType newPotionType = PotionEffectType.getByName(effect);
                if (newPotionType != null) {
                    // Check if it is a valid addition
                    if (newPotionType.equals(PotionEffectType.BLINDNESS) || newPotionType.equals(PotionEffectType.CONFUSION)
                            || newPotionType.equals(PotionEffectType.HUNGER) || newPotionType.equals(PotionEffectType.POISON)
                            || newPotionType.equals(PotionEffectType.SLOW) || newPotionType.equals(PotionEffectType.SLOW_DIGGING)
                            || newPotionType.equals(PotionEffectType.WEAKNESS)) {
                        Settings.acidDamageType.add(newPotionType);
                    }
                } else {
                    getLogger().warning("Could not interpret acid damage modifier: " + effect + " - skipping");
                    getLogger().warning("Types can be : SLOW, SLOW_DIGGING, CONFUSION,");
                    getLogger().warning("BLINDNESS, HUNGER, WEAKNESS and POISON");
                }
            }
        }

        Settings.damageOps = getConfig().getBoolean("general.damageops", false);
        // All done
        return true;
    }


    /**
     * Registers events
     */
    public void registerEvents() {
        final PluginManager manager = getServer().getPluginManager();
        // Ensures Lava flows correctly in AcidRain world
        lavaListener = new LavaCheck(this);
        manager.registerEvents(lavaListener, this);
        // Ensures that water is acid
        manager.registerEvents(new AcidEffect(this), this);
        // Ensures that boats are safe in AcidRain
        if (Settings.acidDamage > 0D) {
            manager.registerEvents(new SafeBoat(this), this);
        }
    }


    /**
     * @return the onePointEight
     */
    public boolean isOnePointEight() {
        // Check server version - check for a class that only 1.8 has
        Class<?> clazz;
        try {
            clazz = Class.forName("org.bukkit.event.player.PlayerInteractAtEntityEvent");
        } catch (Exception e) {
            //getLogger().info("No PlayerInteractAtEntityEvent found.");
            clazz = null;
        }
        if (clazz != null) {
            return true;
        }
        return false;
    }
}
