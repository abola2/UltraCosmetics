package be.isach.ultracosmetics.listeners;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.UltraCosmeticsData;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.suits.ArmorSlot;
import be.isach.ultracosmetics.menu.CosmeticsInventoryHolder;
import be.isach.ultracosmetics.player.UltraPlayer;
import be.isach.ultracosmetics.player.profile.CosmeticsProfile;
import be.isach.ultracosmetics.player.profile.CosmeticsProfileManager;
import be.isach.ultracosmetics.run.FallDamageManager;
import be.isach.ultracosmetics.treasurechests.TreasureRandomizer;
import be.isach.ultracosmetics.util.ItemFactory;
import be.isach.ultracosmetics.util.UCMaterial;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;

/**
 * Player listeners.
 *
 * @author iSach
 * @since 08-03-2015
 */
public class PlayerListener implements Listener {

    private UltraCosmetics ultraCosmetics;

    public PlayerListener(UltraCosmetics ultraCosmetics) {
        this.ultraCosmetics = ultraCosmetics;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(final PlayerJoinEvent event) {
        UltraPlayer cp = ultraCosmetics.getPlayerManager().create(event.getPlayer());
        BukkitRunnable bukkitRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (SettingsManager.getConfig().getBoolean("Menu-Item.Give-On-Join") && event.getPlayer().hasPermission("ultracosmetics.receivechest") && SettingsManager.getConfig().getStringList("Enabled-Worlds").contains(event.getPlayer().getWorld().getName())) {
                    Bukkit.getScheduler().runTaskLater(ultraCosmetics, () -> {
                        if (cp != null && event.getPlayer() != null)
                            cp.giveMenuItem();
                    }, 5);
                }

                if (ultraCosmetics.getUpdateChecker() != null && ultraCosmetics.getUpdateChecker().isOutdated()) {
                    if (event.getPlayer().isOp()) {
                        event.getPlayer().sendMessage(ChatColor.BOLD + "" + ChatColor.ITALIC + "UltraCosmetics > " + ChatColor.RED + "" + ChatColor.BOLD + "An update is available: " + ultraCosmetics.getUpdateChecker().getLastVersion());
                    }
                }

                if (SettingsManager.getConfig().getStringList("Enabled-Worlds").contains(event.getPlayer().getWorld().getName())
                        && UltraCosmeticsData.get().areCosmeticsProfilesEnabled()) {
                    // Cosmetics profile. TODO Add option to disable!!
                    CosmeticsProfileManager cosmeticsProfileManager = ultraCosmetics.getCosmeticsProfileManager();
                    if (cosmeticsProfileManager.getProfile(event.getPlayer().getUniqueId()) == null) {
                        // ultraCosmetics.getSmartLogger().write("Creating cosmetics profile for " + event.getPlayer().getName());
                        cosmeticsProfileManager.initForPlayer(cp);
                    } else {
                        //    ultraCosmetics.getSmartLogger().write("Loading cosmetics profile for " + event.getPlayer().getName());
                        CosmeticsProfile cosmeticsProfile = cosmeticsProfileManager.getProfile(event.getPlayer().getUniqueId());
                        cp.setCosmeticsProfile(cosmeticsProfile);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                cosmeticsProfile.loadToPlayer(cp);
                            }
                        }.runTask(ultraCosmetics);
                    }
                }
            }
        };
        bukkitRunnable.runTaskAsynchronously(ultraCosmetics);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        UltraPlayer ultraPlayer = ultraCosmetics.getPlayerManager().getUltraPlayer(event.getPlayer());
        if (SettingsManager.getConfig().getStringList("Enabled-Worlds").contains(event.getPlayer().getWorld().getName())) {
            if (SettingsManager.getConfig().getBoolean("Menu-Item.Give-On-Join") && event.getPlayer().hasPermission("ultracosmetics.receivechest")) {
                ultraCosmetics.getPlayerManager().getUltraPlayer(event.getPlayer()).giveMenuItem();
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (UltraCosmeticsData.get().areCosmeticsProfilesEnabled()) {
                        CosmeticsProfile cp = ultraCosmetics.getCosmeticsProfileManager().getProfile(event.getPlayer().getUniqueId());
                        if (cp == null) {
                            ultraCosmetics.getCosmeticsProfileManager().initForPlayer(ultraPlayer);
                        } else {
                            cp.loadToPlayer();
                        }
                    }
                }
            }.runTaskLater(ultraCosmetics, 5);
        }
    }

    // run this as early as possible for compatibility with MV-inventories?
    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChangeEarly(final PlayerChangedWorldEvent event) {
        UltraPlayer ultraPlayer = ultraCosmetics.getPlayerManager().getUltraPlayer(event.getPlayer());
        if (!SettingsManager.getConfig().getStringList("Enabled-Worlds").contains(event.getPlayer().getWorld().getName())) {
            // Disable cosmetics when joining a bad world.
            ultraPlayer.removeMenuItem();
            ultraPlayer.setQuitting(true);
            if (ultraPlayer.clear())
                ultraPlayer.getBukkitPlayer().sendMessage(MessageManager.getMessage("World-Disabled"));
            ultraPlayer.setQuitting(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getItemDrop().remove();
            ItemStack chest = event.getPlayer().getItemInHand().clone();
            chest.setAmount(1);
            event.getPlayer().setItemInHand(chest);
            event.getPlayer().updateInventory();
        }
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        UltraPlayer ultraPlayer = ultraCosmetics.getPlayerManager().getUltraPlayer(event.getPlayer());
        // apparently can happen if a player disconnected while on a pressure plate
        if (ultraPlayer == null) return;
        // Avoid triggering this when clicking in the inventory
        InventoryType t = event.getPlayer().getOpenInventory().getType();
        if (t != InventoryType.CRAFTING
                && t != InventoryType.CREATIVE) {
            return;
        }
        if (ultraPlayer.getCurrentTreasureChest() != null) {
            event.setCancelled(true);
            return;
        }
        if (isMenuItem(event.getItem())) {
            event.setCancelled(true);
            ultraCosmetics.getMenus().getMainMenu().open(ultraPlayer);
        }
    }

    /**
     * Cancel players from removing, picking the item in their inventory.
     *
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void cancelMove(InventoryClickEvent event) {
        //Bukkit.getLogger().info("click");
        Player player = (Player) event.getWhoClicked();
        if (!SettingsManager.getConfig().getStringList("Enabled-Worlds").contains(player.getWorld().getName())) return;
        if (event.getView().getTopInventory().getHolder() instanceof CosmeticsInventoryHolder
                || isMenuItem(event.getCurrentItem())
                || (event.getClick() == ClickType.NUMBER_KEY && isMenuItem(player.getInventory().getItem(event.getHotbarButton())))) {
            event.setCancelled(true);
            player.updateInventory();
            //Bukkit.getLogger().info("cancel click");
        }
    }

    /**
     * Cancel players from removing, picking the item in their inventory.
     *
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void cancelMove(InventoryCreativeEvent event) {
        //Bukkit.getLogger().info("creative");
        
        Player player = (Player) event.getWhoClicked();
        if ((SettingsManager.getConfig().getStringList("Enabled-Worlds")).contains(player.getWorld().getName())) {
            if (isMenuItem(event.getCurrentItem())) {
                //Bukkit.getLogger().info("cancel creative");
                event.setCancelled(true);
                player.closeInventory(); // Close the inventory because clicking again results in the event being handled client side
            }
        }
    }

    /**
     * Cancel players from removing, picking the item in their inventory.
     *
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void cancelMove(InventoryDragEvent event) {
        for (ItemStack item : event.getNewItems().values()) {
            if (isMenuItem(item)) {
                event.setCancelled(true);
                ((Player) event.getWhoClicked()).updateInventory();
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if ((boolean) SettingsManager.getConfig().get("Menu-Item.Give-On-Respawn") && ((List<String>) SettingsManager.getConfig().get("Enabled-Worlds")).contains(event.getPlayer().getWorld().getName())) {
            int slot = SettingsManager.getConfig().getInt("Menu-Item.Slot");
            if (event.getPlayer().getInventory().getItem(slot) != null) {
                event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), event.getPlayer().getInventory().getItem(slot));
                event.getPlayer().getInventory().setItem(slot, null);
            }
            String name = ChatColor.translateAlternateColorCodes('&', String.valueOf(SettingsManager.getConfig().get("Menu-Item.Displayname")));
            UCMaterial material = UCMaterial.matchUCMaterial((String) SettingsManager.getConfig().get("Menu-Item.Type"));
            // byte data = Byte.valueOf(String.valueOf(SettingsManager.getConfig().get("Menu-Item.Data"))); TODO
            event.getPlayer().getInventory().setItem(slot, ItemFactory.create(material, name));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        if (ultraCosmetics.getPlayerManager().getUltraPlayer(event.getPlayer()).getCurrentTreasureChest() != null) {
            ultraCosmetics.getPlayerManager().getUltraPlayer(event.getPlayer()).getCurrentTreasureChest().forceOpen(0);
        }
        // TODO: Do anything with cosmetics profile?
        UltraPlayer up = ultraCosmetics.getPlayerManager().getUltraPlayer(event.getPlayer());
        up.setQuitting(true);
        up.clear();
        up.removeMenuItem();
        if (UltraCosmeticsData.get().areCosmeticsProfilesEnabled())
            ultraCosmetics.getCosmeticsProfileManager().clearPlayerFromProfile(up);
        ultraCosmetics.getPlayerManager().remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        int slot = SettingsManager.getConfig().getInt("Menu-Item.Slot");
        if (isMenuItem(event.getEntity().getInventory().getItem(slot))) {
            event.getDrops().remove(event.getEntity().getInventory().getItem(slot));
            event.getEntity().getInventory().setItem(slot, null);
        }
        UltraPlayer ultraPlayer = ultraCosmetics.getPlayerManager().getUltraPlayer(event.getEntity());
        if (ultraPlayer.getCurrentGadget() != null)
            event.getDrops().remove(event.getEntity().getInventory().getItem((Integer) SettingsManager.getConfig().get("Gadget-Slot")));
        if (ultraPlayer.getCurrentHat() != null)
            event.getDrops().remove(ultraPlayer.getCurrentHat().getItemStack());
        Arrays.asList(ArmorSlot.values()).forEach(armorSlot -> {
            if (ultraPlayer.getSuit(armorSlot) != null) {
                event.getDrops().remove(ultraPlayer.getSuit(armorSlot).getItemStack());
            }
        });
        if (ultraPlayer.getCurrentEmote() != null)
            event.getDrops().remove(ultraPlayer.getCurrentEmote().getItemStack());
        ultraPlayer.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && FallDamageManager.shouldBeProtected(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (TreasureRandomizer.fireworksList.contains(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPickUpItem(PlayerPickupItemEvent event) {
        if (isMenuItem(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractGhost(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() != null
                && event.getRightClicked().hasMetadata("C_AD_ArmorStand"))
            event.setCancelled(true);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission("ultracosmetics.bypass.disabledcommands")) return;
        String strippedCommand = event.getMessage().split(" ")[0].replace("/", "").toLowerCase();
        if (!SettingsManager.getConfig().getList("Disabled-Commands").contains(strippedCommand)) return;
        UltraPlayer player = ultraCosmetics.getPlayerManager().getUltraPlayer(event.getPlayer());
        if (player.hasCosmeticsEquipped()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageManager.getMessage("Disabled-Command-Message"));
        }
    }

    private boolean isMenuItem(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', String.valueOf(SettingsManager.getConfig().get("Menu-Item.Displayname"))));
    }
}
