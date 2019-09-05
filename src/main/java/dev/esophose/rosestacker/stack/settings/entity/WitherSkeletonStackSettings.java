package dev.esophose.rosestacker.stack.settings.entity;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Wolf;

public class WitherSkeletonStackSettings extends SkeletonStackSettings {

    public WitherSkeletonStackSettings(YamlConfiguration entitySettingsConfiguration) {
        super(entitySettingsConfiguration);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.WITHER_SKELETON;
    }

}
