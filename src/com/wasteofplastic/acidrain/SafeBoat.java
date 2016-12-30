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
package com.wasteofplastic.acidrain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.material.SimpleAttachableMaterialData;
import org.bukkit.material.TrapDoor;
import org.bukkit.util.Vector;

/**
 * This file improves the safety of boats in AcidIsland It enables
 * players to get out of boats without being dropped into the acid. It
 * enables players to hit a boat and have it pop into their inventory
 * immediately
 * 
 * @author tastybento
 */
public class SafeBoat implements Listener {
    // Flags to indicate if a player has exited a boat recently or not
    private static HashMap<UUID, Entity> exitedBoat = new HashMap<UUID, Entity>();
    // Stores players that should be ignored because they are being teleported away from 
    // a locked islands
    private static Set<UUID> ignoreList = new HashSet<UUID>();

    public SafeBoat(AcidRain aSkyBlock) {
    }

    /**
     * @param e
     *            This event check throws the boat at a player when they hit it
     *            unless someone is in it
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(VehicleDamageEvent e) {
        // plugin.getLogger().info("Damage event " + e.getDamage());
        // Find out what block is being clicked
        Vehicle boat = e.getVehicle();
        if (!(boat instanceof Boat)) {
            return;
        }
        if (!boat.isEmpty()) {
            return;
        }
        final World playerWorld = boat.getWorld();
        if (!AcidRain.getAcidRainWorlds().contains(playerWorld)) {
            // Not the right world
            return;
        }
        // plugin.getLogger().info("Boat ");
        // Find out who is doing the clicking
        if (!(e.getAttacker() instanceof Player)) {
            // If a creeper blows up the boat, tough cookies!
            return;
        }
        Player p = (Player) e.getAttacker();
        if (p == null) {
            return;
        }
        // Try to remove the boat and throw it at the player
        Location boatSpot = new Location(boat.getWorld(), boat.getLocation().getX(), boat.getLocation().getY() + 2, boat.getLocation().getZ());
        Location throwTo = new Location(boat.getWorld(), p.getLocation().getX(), p.getLocation().getY() + 1, p.getLocation().getZ());
        ItemStack newBoat = new ItemStack(Material.BOAT, 1);
        // Find the direction the boat should move in
        Vector dir = throwTo.toVector().subtract(boatSpot.toVector()).normalize();
        dir = dir.multiply(0.5);
        Entity newB = boat.getWorld().dropItem(boatSpot, newBoat);
        newB.setVelocity(dir);
        boat.remove();
        e.setCancelled(true);
    }

    /**
     * @param e
     *            This function prevents boats from exploding when they hit
     *            something
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onBoatHit(VehicleDestroyEvent e) {
        // plugin.getLogger().info("Vehicle destroyed event called");
        final Entity boat = e.getVehicle();
        if (!(boat instanceof Boat)) {
            return;
        }
        if (!AcidRain.getAcidRainWorlds().contains(boat.getWorld())) {
            // Not the right world
            return;
        }
        if (!(e.getAttacker() instanceof Player)) {
            // plugin.getLogger().info("Attacker is not a player so cancel event");
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleport(final PlayerTeleportEvent e) {
        //
        // plugin.getLogger().info("DEBUG: Teleport called");
        Player player = e.getPlayer();
        if (SafeBoat.ignoreList.contains(player.getUniqueId())) {
            return;
        }
        // If the player is not teleporting due to boat exit, return
        if (!exitedBoat.containsKey(player.getUniqueId())) {
            return;
        }
        // Entity boat = exitedBoat.get(player.getUniqueId());
        // Reset the flag
        exitedBoat.remove(player.getUniqueId());
        // Okay, so a player is getting out of a boat in the the right world.
        // Now...
        //plugin.getLogger().info("DEBUG: Player just exited a boat");
        // Find a safe place for the player to land
        int radius = 0;
        while (radius++ < 2) {
            for (int x = player.getLocation().getBlockX() - radius; x < player.getLocation().getBlockX() + radius; x++) {
                for (int z = player.getLocation().getBlockZ() - radius; z < player.getLocation().getBlockZ() + radius; z++) {
                    for (int y = player.getLocation().getBlockY(); y < player.getLocation().getBlockY() + 2; y++) {
                        // The safe location to tp to is actually +0.5 to x and
                        // z.
                        final Location loc = new Location(player.getWorld(), (double) (x + 0.5), (double) y, (double) (z + 0.5));
                        // plugin.getLogger().info("XYZ is " + x + " " + y + " "
                        // + z);
                        // Make sure the location is safe
                        if (isSafeLocation(loc)) {
                            // plugin.getLogger().info("Safe!");
                            e.setTo(loc);
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * @param e
     *            This event aims to put the player in a safe place when they
     *            exit the boat
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBoatExit(VehicleExitEvent e) {
        final Entity boat = e.getVehicle();
        if (!boat.getType().equals(EntityType.BOAT)) {
            // Not a boat
            return;
        }
        // LivingEntity entity = e.getExited();
        final Entity entityObj = (Entity) e.getExited();
        if (!(entityObj instanceof Player)) {
            return;
        }
        final Player player = (Player) entityObj;
        final World playerWorld = player.getWorld();
        if (!AcidRain.getAcidRainWorlds().contains(playerWorld)) {
            // Not the right world
            return;
        }
        if (SafeBoat.ignoreList.contains(player.getUniqueId())) {
            return;
        }
        // Set the boat exit flag for this player
        // midTeleport.add(player.getUniqueId());
        if (exitedBoat.containsKey(player.getUniqueId())) {
            // Debounce
            e.setCancelled(true);
        } else {
            exitedBoat.put(player.getUniqueId(), boat);
        }
        return;
    }

    /**
     * Checks if this location is safe for a player to teleport to. Used by
     * warps and boat exits Unsafe is any liquid or air and also if there's no
     * space
     * 
     * @param l
     *            - Location to be checked
     * @return true if safe, otherwise false
     */
    public static boolean isSafeLocation(final Location l) {
        if (l == null) {
            return false;
        }
        // TODO: improve the safe location finding.
        //Bukkit.getLogger().info("DEBUG: " + l.toString());
        final Block ground = l.getBlock().getRelative(BlockFace.DOWN);
        final Block space1 = l.getBlock();
        final Block space2 = l.getBlock().getRelative(BlockFace.UP);
        //Bukkit.getLogger().info("DEBUG: ground = " + ground.getType());
        //Bukkit.getLogger().info("DEBUG: space 1 = " + space1.getType());
        //Bukkit.getLogger().info("DEBUG: space 2 = " + space2.getType());
        // Portals are not "safe"
        if (space1.getType() == Material.PORTAL || ground.getType() == Material.PORTAL || space2.getType() == Material.PORTAL
                || space1.getType() == Material.ENDER_PORTAL || ground.getType() == Material.ENDER_PORTAL || space2.getType() == Material.ENDER_PORTAL) {
            return false;
        }
        // If ground is AIR, then this is either not good, or they are on slab,
        // stair, etc.
        if (ground.getType() == Material.AIR) {
            // Bukkit.getLogger().info("DEBUG: air");
            return false;
        }
        // In aSkyblock, liquid may be unsafe
        if (ground.isLiquid() || space1.isLiquid() || space2.isLiquid()) {
            // Check if acid has no damage
            if (Settings.acidDamage > 0D) {
                // Bukkit.getLogger().info("DEBUG: acid");
                return false;
            } else if (ground.getType().equals(Material.STATIONARY_LAVA) || ground.getType().equals(Material.LAVA)
                    || space1.getType().equals(Material.STATIONARY_LAVA) || space1.getType().equals(Material.LAVA)
                    || space2.getType().equals(Material.STATIONARY_LAVA) || space2.getType().equals(Material.LAVA)) {
                // Lava check only
                // Bukkit.getLogger().info("DEBUG: lava");
                return false;
            }
        }
        MaterialData md = ground.getState().getData();
        if (md instanceof SimpleAttachableMaterialData) {
            //Bukkit.getLogger().info("DEBUG: trapdoor/button/tripwire hook etc.");
            if (md instanceof TrapDoor) {
                TrapDoor trapDoor = (TrapDoor)md;
                if (trapDoor.isOpen()) {
                    //Bukkit.getLogger().info("DEBUG: trapdoor open");
                    return false;
                }
            } else {
                return false;
            }
            //Bukkit.getLogger().info("DEBUG: trapdoor closed");
        }
        if (ground.getType().equals(Material.CACTUS) || ground.getType().equals(Material.BOAT) || ground.getType().equals(Material.FENCE)
                || ground.getType().equals(Material.NETHER_FENCE) || ground.getType().equals(Material.SIGN_POST) || ground.getType().equals(Material.WALL_SIGN)) {
            // Bukkit.getLogger().info("DEBUG: cactus");
            return false;
        }
        // Check that the space is not solid
        // The isSolid function is not fully accurate (yet) so we have to
        // check
        // a few other items
        // isSolid thinks that PLATEs and SIGNS are solid, but they are not
        if (space1.getType().isSolid() && !space1.getType().equals(Material.SIGN_POST) && !space1.getType().equals(Material.WALL_SIGN)) {
            return false;
        }
        if (space2.getType().isSolid()&& !space2.getType().equals(Material.SIGN_POST) && !space2.getType().equals(Material.WALL_SIGN)) {
            return false;
        }
        // Safe
        //Bukkit.getLogger().info("DEBUG: safe!");
        return true;
    }

    /**
     * Temporarily ignore a player
     * @param player
     */
    public static void setIgnore(UUID player) {
        if (SafeBoat.ignoreList.contains(player)) {
            SafeBoat.ignoreList.remove(player);
        } else {
            SafeBoat.ignoreList.add(player);
        }
    }
}