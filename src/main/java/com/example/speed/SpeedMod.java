package fun.rich.utils.features.aura.rotations.impl;

import fun.rich.Rich;
import fun.rich.features.impl.combat.Aura;
import fun.rich.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.rich.utils.features.aura.striking.StrikeManager;
import fun.rich.utils.features.aura.utils.MathAngle;
import fun.rich.utils.features.aura.warp.Turns;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.security.SecureRandom;

public class LGAngle extends RotateConstructor {
    public LGAngle() {
        super("CakeWorld");
    }

    @Override
    public Turns limitAngleChange(Turns currentAngle, Turns targetAngle, Vec3d vec3d, Entity entity) {
        Turns angleDelta = MathAngle.calculateDelta(currentAngle, targetAngle);
        float yawDelta = angleDelta.getYaw(), pitchDelta = angleDelta.getPitch();
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));

        Aura aura = Aura.getInstance();
        boolean атака = lolikbypass(Rich.getInstance().getAttackPerpetrator().getAttackHandler(), 0);
        boolean pa = aura.getTarget() != null && MathAngle.rayTrace(currentAngle.getYaw(), currentAngle.getPitch(), aura.getAttackRange().getValue(), aura.getLookRange().getValue(), aura.getTarget());

        float yaw = 0;
        float pitch = 0;
        if (aura.getTarget() != null && !атака) {
            double градуси = 10.0;
            double period = 10.0;
            double максамп = 10.0;
            double радс = Math.toRadians(градуси);
            double t = System.currentTimeMillis() / (period * 55);
            double амп = максамп * Math.sin(t * Math.PI * 2);

            yaw = (float) (амп * Math.cos(радс));
            pitch = (float) (амп * Math.sin(радс));
            pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        }

        float скорост = атака ? 0.45F : (lolikbypass(Rich.getInstance().getAttackPerpetrator().getAttackHandler(), 0) ? 1F : 0.3F);
        if (атака && !pa) {
            скорост = 0.45F;
        }

        float lineYaw = (Math.abs(yawDelta / rotationDifference) * 180);
        float linePitch = (Math.abs(pitchDelta) * 180);
        float moveYaw = MathHelper.clamp(yawDelta, -lineYaw, lineYaw);
        float movePitch = MathHelper.clamp(pitchDelta, -linePitch, linePitch);
        float targetYaw = currentAngle.getYaw() + moveYaw;

        Turns moveAngle = new Turns(currentAngle.getYaw(), currentAngle.getPitch());
        moveAngle.setYaw(MathHelper.lerp(Math.clamp(randomLerp(скорост, скорост + 0.2F), 0f, 1f), currentAngle.getYaw(), targetYaw) + yaw);

        float calculatedPitch = MathHelper.lerp(
                Math.clamp(randomLerp(скорост, скорост + 0.2F), 0f, 1f),
                currentAngle.getPitch(),
                currentAngle.getPitch() + movePitch
        ) + pitch;

        float clampedPitch = MathHelper.clamp(calculatedPitch, -90.0f, 90.0f);
        moveAngle.setPitch(clampedPitch);
        return moveAngle;
    }

    private boolean lolikbypass(StrikeManager strikeManager, int ticks) {
        Aura aura = Aura.getInstance();
        return aura.getTarget() != null && Rich.getInstance().getAttackPerpetrator().getAttackHandler().canAttack(aura.getConfig(), ticks);
    }

    private float randomLerp(float min, float max) {
        return MathHelper.lerp(new SecureRandom().nextFloat(), min, max);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0, 0, 0);
    }
}
