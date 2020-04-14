package minegame159.meteorclient.modules.combat;

import com.google.common.collect.Streams;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import minegame159.meteorclient.accountsfriends.FriendManager;
import minegame159.meteorclient.events.TickEvent;
import minegame159.meteorclient.mixininterface.IVec3d;
import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.ToggleModule;
import minegame159.meteorclient.settings.*;
import minegame159.meteorclient.utils.EntityUtils;
import net.minecraft.command.arguments.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RayTraceContext;

public class KillAura extends ToggleModule {
    public enum Priority {
        LowestDistance,
        HighestDistance,
        LowestHealth,
        HighestHealth
    }

    public Setting<Double> range = addSetting(new DoubleSetting.Builder()
            .name("range")
            .description("Attack range.")
            .group("General")
            .defaultValue(5.5)
            .min(0.0)
            .build()
    );

    public Setting<Boolean> ignoreWalls = addSetting(new BoolSetting.Builder()
            .name("ignore-walls")
            .description("Attack through walls.")
            .group("General")
            .defaultValue(true)
            .build()
    );

    public Setting<Priority> priority = addSetting(new EnumSetting.Builder<Priority>()
            .name("priority")
            .description("What entities to target.")
            .group("General")
            .defaultValue(Priority.LowestHealth)
            .build()
    );

    private Setting<Boolean> rotate = addSetting(new BoolSetting.Builder()
            .name("rotate")
            .description("Rotates you towards the target.")
            .group("General")
            .defaultValue(false)
            .build()
    );

    public Setting<Boolean> players = addSetting(new BoolSetting.Builder()
            .name("players")
            .description("Attack players.")
            .group("To Attack")
            .defaultValue(true)
            .build()
    );

    public Setting<Boolean> friends = addSetting(new BoolSetting.Builder()
            .name("friends")
            .description("Attack friends, useful only if attack players is on.")
            .group("To Attack")
            .defaultValue(false)
            .build()
    );

    public Setting<Boolean> animals = addSetting(new BoolSetting.Builder()
            .name("animals")
            .description("Attack animals.")
            .group("To Attack")
            .defaultValue(true)
            .build()
    );

    public Setting<Boolean> mobs = addSetting(new BoolSetting.Builder()
            .name("mobs")
            .description("Attack mobs.")
            .group("To Attack")
            .defaultValue(true)
            .build()
    );

    private Setting<Integer> hitDelay;
    private Setting<Boolean> smartDelay = addSetting(new BoolSetting.Builder()
            .name("smart-delay")
            .description("Smart delay.")
            .group("Delay")
            .onChanged(aBoolean -> {
                hitDelay.setVisible(!aBoolean);
                hitDelayTimer = 0;
            })
            .defaultValue(true)
            .build()
    );

    private int hitDelayTimer;

    private Vec3d vec3d1 = new Vec3d(0, 0, 0);
    private Vec3d vec3d2 = new Vec3d(0, 0, 0);

    public KillAura() {
        super(Category.Combat, "kill-aura", "Automatically attacks entities.");

        hitDelay = addSetting(new IntSetting.Builder()
                .name("hit-delay")
                .description("Hit delay in ticks. 20 ticks = 1 second.")
                .group("Delay")
                .defaultValue(0)
                .min(0)
                .visible(false)
                .build()
        );
    }

    @Override
    public void onActivate() {
        hitDelayTimer = 0;
    }

    private boolean isInRange(Entity entity) {
        return entity.distanceTo(mc.player) <= range.get();
    }

    private boolean canAttackEntity(Entity entity) {
        if (entity.getUuid().equals(mc.player.getUuid())) return false;
        if (EntityUtils.isPlayer(entity) && players.get()) {
            if (friends.get()) return true;
            return !FriendManager.INSTANCE.contains((PlayerEntity) entity);
        }
        if (EntityUtils.isAnimal(entity) && animals.get()) return true;
        return EntityUtils.isMob(entity) && mobs.get();
    }

    private boolean canSeeEntity(Entity entity) {
        if (ignoreWalls.get()) return true;

        ((IVec3d) vec3d1).set(mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight(), mc.player.getZ());
        ((IVec3d) vec3d2).set(entity.getX(), entity.getY(), entity.getZ());
        boolean canSeeFeet =  mc.world.rayTrace(new RayTraceContext(vec3d1, vec3d2, RayTraceContext.ShapeType.COLLIDER, RayTraceContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;

        ((IVec3d) vec3d2).set(entity.getX(), entity.getY() + entity.getStandingEyeHeight(), entity.getZ());
        boolean canSeeEyes =  mc.world.rayTrace(new RayTraceContext(vec3d1, vec3d2, RayTraceContext.ShapeType.COLLIDER, RayTraceContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;

        return canSeeFeet || canSeeEyes;
    }

    private int invertSort(int sort) {
        if (sort == 0) return 0;
        return sort > 0 ? -1 : 1;
    }

    private int sort(LivingEntity e1, LivingEntity e2) {
        switch (priority.get()) {
            case LowestDistance:  return Double.compare(e1.distanceTo(mc.player), e2.distanceTo(mc.player));
            case HighestDistance: return invertSort(Double.compare(e1.distanceTo(mc.player), e2.distanceTo(mc.player)));
            case LowestHealth:    return Float.compare(e1.getHealth(), e2.getHealth());
            case HighestHealth:   return invertSort(Float.compare(e1.getHealth(), e2.getHealth()));
            default:              return 0;
        }
    }

    @EventHandler
    private Listener<TickEvent> onTick = new Listener<>(event -> {
        if (mc.player.getHealth() <= 0) return;

        if (smartDelay.get()) {
            // Smart delay
            if (mc.player.getAttackCooldownProgress(0.5f) < 1) return;
        } else {
            // Manual delay
            if (hitDelayTimer < hitDelay.get()) {
                hitDelayTimer++;
                return;
            }
            else hitDelayTimer = 0;
        }

        Streams.stream(mc.world.getEntities())
                .filter(this::isInRange)
                .filter(this::canAttackEntity)
                .filter(this::canSeeEntity)
                .map(entity -> (LivingEntity) entity)
                .filter(entity -> entity.getHealth() > 0)
                .min(this::sort)
                .ifPresent(entity -> {
                    if (rotate.get()) {
                        ((IVec3d) vec3d1).set(entity.getX(), entity.getY() + entity.getHeight() / 2, entity.getZ());
                        mc.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, vec3d1);
                    }
                    mc.interactionManager.attackEntity(mc.player, entity);
                    mc.player.swingHand(Hand.MAIN_HAND);
                });
    });
}
