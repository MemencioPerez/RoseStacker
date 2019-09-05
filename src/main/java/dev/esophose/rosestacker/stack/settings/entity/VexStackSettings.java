package dev.esophose.rosestacker.stack.settings.entity;

import dev.esophose.rosestacker.stack.StackedEntity;
import dev.esophose.rosestacker.stack.settings.EntityStackSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Vex;

public class VexStackSettings extends EntityStackSettings {

    private boolean dontStackIfCharging;

    public VexStackSettings(YamlConfiguration entitySettingsConfiguration) {
        super(entitySettingsConfiguration);

        this.dontStackIfCharging = entitySettingsConfiguration.getBoolean("dont-stack-if-charging");
    }

    @Override
    protected boolean canStackWithInternal(StackedEntity stack1, StackedEntity stack2) {
        Vex vex1 = (Vex) stack1.getEntity();
        Vex vex2 = (Vex) stack2.getEntity();

        return !this.dontStackIfCharging || (!vex1.isCharging() && !vex2.isCharging());
    }

    @Override
    protected void setDefaultsInternal() {
        this.setIfNotExists("dont-stack-if-charging", false);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.VEX;
    }

}
