package dev.rosewood.rosestacker.listener;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.manager.StackManager;
import dev.rosewood.rosestacker.stack.EntityStackComparisonResult;
import dev.rosewood.rosestacker.stack.StackedBlock;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.StackedItem;
import dev.rosewood.rosestacker.stack.StackedSpawner;
import dev.rosewood.rosestacker.utils.EntityUtils;
import dev.rosewood.rosestacker.utils.ItemUtils;
import dev.rosewood.rosestacker.utils.PersistentDataUtils;
import dev.rosewood.rosestacker.utils.StackerUtils;
import dev.rosewood.rosestacker.utils.ThreadUtils;
import dev.rosewood.rosestacker.utils.VersionUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class StackToolListener implements Listener {

    private final StackManager stackManager;
    private final LocaleManager localeManager;

    private final Map<UUID, SelectedEntities> selectedEntities;

    public StackToolListener(RosePlugin rosePlugin) {
        this.stackManager = rosePlugin.getManager(StackManager.class);
        this.localeManager = rosePlugin.getManager(LocaleManager.class);

        this.selectedEntities = new HashMap<>();
    }

    /**
     * Handles Right Clicking for StackedEntities.
     * Handles Shift Right Clicking for StackedEntities.
     *
     * @param event The PlayerInteractAtEntityEvent
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!(event.getRightClicked() instanceof LivingEntity entity)
                || event.getHand() != EquipmentSlot.HAND
                || !ItemUtils.isStackingTool(tool))
            return;

        event.setCancelled(true);

        if (!player.hasPermission("rosestacker.stacktool")) {
            this.localeManager.sendCommandMessage(player, "command-stacktool-no-permission");
            return;
        }

        StackedEntity stackedEntity = this.stackManager.getStackedEntity(entity);
        if (stackedEntity == null) {
            this.localeManager.sendCommandMessage(player, "command-stacktool-invalid-entity");
            return;
        }

        if (!player.isSneaking()) {
            boolean stackable = !PersistentDataUtils.isUnstackable(entity);
            PersistentDataUtils.setUnstackable(entity, stackable);
            String stackableStr = !stackable ? "stackable" : "unstackable";
            this.localeManager.sendCommandMessage(player, "command-stacktool-marked-" + stackableStr, StringPlaceholders.of("type", stackedEntity.getStackSettings().getDisplayName()));
        } else {
            PersistentDataUtils.setUnstackable(entity, true);
            ThreadUtils.runAsync(() -> stackedEntity.getDataStorage().forEachTransforming(x -> {
                PersistentDataUtils.setUnstackable(x, true);
                return true;
            }));
            this.localeManager.sendCommandMessage(player, "command-stacktool-marked-all-unstackable", StringPlaceholders.of("type", stackedEntity.getStackSettings().getDisplayName()));
        }
    }

    /**
     * Handles Shift Left Clicks for StackedEntities
     *
     * @param event The EntityDamageByEntityEvent
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClickEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player))
            return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!ItemUtils.isStackingTool(tool))
            return;

        event.setCancelled(true);

        if (!(event.getEntity() instanceof LivingEntity entity))
            return;

        if (!player.hasPermission("rosestacker.stacktool")) {
            this.localeManager.sendCommandMessage(player, "command-stacktool-no-permission");
            return;
        }

        StackedEntity stackedEntity = this.stackManager.getStackedEntity(entity);
        if (stackedEntity == null) {
            this.localeManager.sendCommandMessage(player, "command-stacktool-invalid-entity");
            return;
        }

        if (!player.isSneaking()) {
            SelectedEntities selected = this.selectedEntities.get(player.getUniqueId());
            if (selected == null) {
                selected = new SelectedEntities();
                this.selectedEntities.put(player.getUniqueId(), selected);
            }

            if (selected.getEntity1() == stackedEntity) {
                selected.unselect();
                this.localeManager.sendCommandMessage(player, "command-stacktool-unselect-1", StringPlaceholders.of("type", stackedEntity.getStackSettings().getDisplayName()));
                return;
            }

            selected.select(stackedEntity);

            if (!selected.hasSelected()) {
                this.localeManager.sendCommandMessage(player, "command-stacktool-select-1", StringPlaceholders.of("type", stackedEntity.getStackSettings().getDisplayName()));
            } else {
                this.localeManager.sendCommandMessage(player, "command-stacktool-select-2", StringPlaceholders.of("type", stackedEntity.getStackSettings().getDisplayName()));

                StackedEntity entity1 = selected.getEntity1();
                StackedEntity entity2 = selected.getEntity2();
                selected.unselect();

                EntityStackComparisonResult result = stackedEntity.getStackSettings().canStackWith(entity1, entity2, false, true);
                if (result == EntityStackComparisonResult.CAN_STACK) {
                    this.localeManager.sendCommandMessage(player, "command-stacktool-can-stack");
                } else {
                    this.localeManager.sendCommandMessage(player, "command-stacktool-can-not-stack", StringPlaceholders.of("reason", result.name()));
                }
            }
        } else {
            String trueStr = this.localeManager.getLocaleMessage("command-stacktool-info-true");
            String falseStr = this.localeManager.getLocaleMessage("command-stacktool-info-false");

            this.localeManager.sendCommandMessage(player, "command-stacktool-info");
            this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-uuid", StringPlaceholders.of("uuid", entity.getUniqueId().toString()));
            this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-entity-id", StringPlaceholders.of("id", StackerUtils.formatNumber(entity.getEntityId())));
            this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-entity-type", StringPlaceholders.of("type", entity.getType().name()));
            this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-stack-size", StringPlaceholders.of("amount", StackerUtils.formatNumber(stackedEntity.getStackSize())));
            this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-data-storage-type", StringPlaceholders.of("type", stackedEntity.getDataStorage().getType().name()));
            if (entity.getCustomName() != null)
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-custom-name", StringPlaceholders.of("name", entity.getCustomName()));
            this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-entity-stackable", StringPlaceholders.of("value", PersistentDataUtils.isUnstackable(entity) ? falseStr : trueStr));
            this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-entity-from-spawner", StringPlaceholders.of("value", PersistentDataUtils.isSpawnedFromSpawner(entity) ? trueStr : falseStr));
            this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-entity-has-ai", StringPlaceholders.of("value", !PersistentDataUtils.isAiDisabled(entity) && entity.hasAI() ? trueStr : falseStr));
            this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-location", StringPlaceholders.builder("x", StackerUtils.formatNumber(entity.getLocation().getBlockX()))
                    .add("y", StackerUtils.formatNumber(entity.getLocation().getBlockY()))
                    .add("z", StackerUtils.formatNumber(entity.getLocation().getBlockZ()))
                    .add("world", entity.getWorld().getName()).build());
            this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-chunk", StringPlaceholders.builder("x", StackerUtils.formatNumber(entity.getLocation().getChunk().getX()))
                    .add("z", StackerUtils.formatNumber(entity.getLocation().getChunk().getZ())).build());
        }
    }

    /**
     * Handles Shift Left Click for StackedItems
     *
     * @param event The PlayerInteractEvent
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (event.getAction() == Action.PHYSICAL || !ItemUtils.isStackingTool(tool))
            return;

        event.setCancelled(true);

        if (!player.isSneaking() || event.getHand() != EquipmentSlot.HAND)
            return;

        if (!player.hasPermission("rosestacker.stacktool")) {
            this.localeManager.sendCommandMessage(player, "command-stacktool-no-permission");
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
                if (entity.getType() != VersionUtils.ITEM)
                    continue;

                Item item = (Item) entity;
                if (!EntityUtils.isLookingAtItem(player, item))
                    continue;

                StackedItem stackedItem = this.stackManager.getStackedItem(item);
                if (stackedItem == null)
                    continue;

                ItemMeta itemMeta = item.getItemStack().getItemMeta();

                this.localeManager.sendCommandMessage(player, "command-stacktool-info");
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-uuid", StringPlaceholders.of("uuid", item.getUniqueId().toString()));
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-entity-id", StringPlaceholders.of("id", StackerUtils.formatNumber(item.getEntityId())));
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-item-type", StringPlaceholders.of("type", item.getItemStack().getType().name()));
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-stack-size", StringPlaceholders.of("amount", StackerUtils.formatNumber(stackedItem.getStackSize())));
                if (itemMeta != null && itemMeta.hasDisplayName())
                    this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-custom-name", StringPlaceholders.of("name", itemMeta.getDisplayName()));
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-location", StringPlaceholders.builder("x", StackerUtils.formatNumber(item.getLocation().getBlockX()))
                        .add("y", StackerUtils.formatNumber(item.getLocation().getBlockY()))
                        .add("z", StackerUtils.formatNumber(item.getLocation().getBlockZ()))
                        .add("world", item.getWorld().getName()).build());
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-chunk", StringPlaceholders.builder("x", StackerUtils.formatNumber(item.getLocation().getChunk().getX()))
                        .add("z", StackerUtils.formatNumber(item.getLocation().getChunk().getZ())).build());

                return;
            }
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null)
                return;

            if (clickedBlock.getType() != Material.SPAWNER) {
                StackedBlock stackedBlock = this.stackManager.getStackedBlock(clickedBlock);
                if (stackedBlock == null)
                    return;

                this.localeManager.sendCommandMessage(player, "command-stacktool-info");
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-block-type", StringPlaceholders.of("type", clickedBlock.getType().name()));
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-stack-size", StringPlaceholders.of("amount", StackerUtils.formatNumber(stackedBlock.getStackSize())));
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-location", StringPlaceholders.builder("x", StackerUtils.formatNumber(clickedBlock.getX()))
                        .add("y", StackerUtils.formatNumber(clickedBlock.getY()))
                        .add("z", StackerUtils.formatNumber(clickedBlock.getZ()))
                        .add("world", clickedBlock.getWorld().getName()).build());
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-chunk", StringPlaceholders.builder("x", StackerUtils.formatNumber(clickedBlock.getChunk().getX()))
                        .add("z", StackerUtils.formatNumber(clickedBlock.getChunk().getZ())).build());
            } else {
                StackedSpawner stackedSpawner = this.stackManager.getStackedSpawner(clickedBlock);
                if (stackedSpawner == null)
                    return;

                this.localeManager.sendCommandMessage(player, "command-stacktool-info");
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-spawner-type", StringPlaceholders.of("type", stackedSpawner.getSpawnerTile().getSpawnerType().getEnumName()));
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-stack-size", StringPlaceholders.of("amount", StackerUtils.formatNumber(stackedSpawner.getStackSize())));
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-location", StringPlaceholders.builder("x", StackerUtils.formatNumber(clickedBlock.getX()))
                        .add("y", StackerUtils.formatNumber(clickedBlock.getY()))
                        .add("z", StackerUtils.formatNumber(clickedBlock.getZ()))
                        .add("world", clickedBlock.getWorld().getName()).build());
                this.localeManager.sendSimpleCommandMessage(player, "command-stacktool-info-chunk", StringPlaceholders.builder("x", StackerUtils.formatNumber(clickedBlock.getChunk().getX()))
                        .add("z", StackerUtils.formatNumber(clickedBlock.getChunk().getZ())).build());
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null || clickedBlock.getType() != Material.SPAWNER)
                return;

            StackedSpawner stackedSpawner = this.stackManager.getStackedSpawner(clickedBlock);
            if (stackedSpawner == null) {
                CreatureSpawner creatureSpawner = (CreatureSpawner) clickedBlock.getState();
                creatureSpawner.setDelay(5);
                creatureSpawner.update();
            } else {
                stackedSpawner.getSpawnerTile().setDelay(5);
            }

            int points = 50;
            for (int i = 0; i < points; i++) {
                double dx = Math.cos(Math.PI * 2 * ((double) i / points)) * 0.25;
                double dy = 0.5;
                double dz = Math.sin(Math.PI * 2 * ((double) i / points)) * 0.25;
                double angle = Math.atan2(dz, dx);
                double xAng = Math.cos(angle);
                double zAng = Math.sin(angle);
                clickedBlock.getWorld().spawnParticle(Particle.END_ROD, clickedBlock.getLocation().add(0.5 + dx, dy, 0.5 + dz), 0, xAng, 0, zAng, 0.15);
            }
        }

        // Make the item bob a little bit
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.selectedEntities.remove(event.getPlayer().getUniqueId());
    }

    private static class SelectedEntities {

        private StackedEntity entity1, entity2;

        public void select(StackedEntity entity) {
            if (this.entity1 == null) {
                this.entity1 = entity;
            } else if (this.entity2 == null) {
                this.entity2 = entity;
            }
        }

        public void unselect() {
            this.entity1 = null;
            this.entity2 = null;
        }

        public boolean hasSelected() {
            return this.entity1 != null && this.entity2 != null;
        }

        public StackedEntity getEntity1() {
            return this.entity1;
        }

        public StackedEntity getEntity2() {
            return this.entity2;
        }

    }

}
