package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean killaura = false;
    private static boolean lastR = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static long lastScreenShakeTime = 0;
    private static final Random random = new Random();
    private static final TSAngle rotator = new TSAngle();
    private static int attackCount = 0;

    // ========== НАСТРОЙКИ ПОД МЕЧ ==========
    private static final float RANGE = 4.2f;
    private static final long MIN_DELAY = 720L;           // 0.720 сек
    private static final long MAX_DELAY = 830L;           // 0.830 сек
    private static final float YAW_SPEED_BASE = 25.0f;
    private static final float YAW_SPEED_VARIATION = 12.0f;
    private static final float PITCH_SPEED_FACTOR_MIN = 0.4f;
    private static final float PITCH_SPEED_FACTOR_MAX = 0.9f;
    private static final int SHAKE_PIXELS = 2;
    private static final long SHAKE_INTERVAL_MS = 50;
    private static final int MISS_CHANCE_PERCENT = 18;    // Шанс промаха 18%

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException e) { break; }
                if (mc.player == null) continue;
                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastR) {
                    killaura = !killaura;
                    mc.player.sendMessage(Text.literal(killaura ? "§aKillaura (SWORD) ON" : "§cKillaura OFF"), true);
                    if (!killaura) target = null;
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;
                if (killaura) tick();
            }
        }).start();
    }

    private void tick() {
        performScreenShake();
        
        if (random.nextInt(100) < 3) {
            try { Thread.sleep(random.nextInt(15, 45)); } catch (InterruptedException e) {}
        }
        
        updateTarget();
        if (target == null) return;

        if (mc.player.squaredDistanceTo(target) > RANGE * RANGE) return;
        
        boolean canSee = mc.player.canSee(target);
        if (!canSee && random.nextFloat() < 0.3f) return;
        if (!canSee && random.nextFloat() < 0.15f) canSee = true;

        Vec3d eye = mc.player.getEyePos();
        Vec3d targetCenter = target.getBoundingBox().getCenter();
        float aimOffsetX = (random.nextFloat() - 0.5f) * 0.06f;
        float aimOffsetY = (random.nextFloat() - 0.5f) * 0.04f;
        float aimOffsetZ = (random.nextFloat() - 0.5f) * 0.04f;
        Vec3d to = targetCenter.add(aimOffsetX, aimOffsetY, aimOffsetZ).subtract(eye);
        
        double hyp = Math.hypot(to.x, to.z);
        float idealYaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
        float idealPitch = (float) -Math.toDegrees(Math.atan2(to.y, hyp));
        idealYaw = wrap(idealYaw);
        idealPitch = clamp(idealPitch, -89, 89);

        Turns current = new Turns(mc.player.getYaw(), mc.player.getPitch());
        Turns targetAngles = new Turns(idealYaw, idealPitch);
        
        float dynamicYawSpeed = YAW_SPEED_BASE + random.nextFloat() * YAW_SPEED_VARIATION;
        float dynamicPitchSpeed = dynamicYawSpeed * (PITCH_SPEED_FACTOR_MIN + random.nextFloat() * (PITCH_SPEED_FACTOR_MAX - PITCH_SPEED_FACTOR_MIN));
        
        Turns newAngles = rotator.limitAngleChange(current, targetAngles, dynamicYawSpeed, dynamicPitchSpeed);
        mc.player.setYaw(newAngles.getYaw());
        mc.player.setPitch(newAngles.getPitch());
        mc.player.headYaw = newAngles.getYaw();
        mc.player.bodyYaw = newAngles.getYaw();

        long now = System.currentTimeMillis();
        long baseDelay = MIN_DELAY + (long)(random.nextDouble() * (MAX_DELAY - MIN_DELAY));
        
        long fatigueDelay = (long)(baseDelay + (attackCount / 12) * 2);
        fatigueDelay = Math.min(fatigueDelay, MAX_DELAY + 30);
        
        float deltaYaw = wrap(idealYaw - mc.player.getYaw());
        float deltaPitch = idealPitch - mc.player.getPitch();
        float angleTolerance = 8.0f + random.nextFloat() * 7.0f;
        boolean canAttack = Math.abs(deltaYaw) < angleTolerance && Math.abs(deltaPitch) < angleTolerance;
        
        // Шанс промаха 18%
        boolean missChance = random.nextInt(100) < MISS_CHANCE_PERCENT;
        
        if (now - lastAttackTime >= fatigueDelay && canAttack && !missChance) {
            boolean wasSprinting = mc.player.isSprinting();
            
            if (random.nextInt(100) < 15) {
                try { Thread.sleep(random.nextInt(1, 8)); } catch (InterruptedException e) {}
            }
            
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            
            if (wasSprinting) mc.player.setSprinting(true);
            mc.player.setSprinting(true);
            
            lastAttackTime = now;
            attackCount++;
            
            if (attackCount % 7 == 0 && random.nextBoolean()) {
                forceRescanTarget();
            }
        }
        
        if (random.nextInt(300) < 2) {
            try { Thread.sleep(random.nextInt(20, 60)); } catch (InterruptedException e) {}
        }
    }
    
    private void performScreenShake() {
        if (mc.getWindow() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScreenShakeTime < SHAKE_INTERVAL_MS) return;
        lastScreenShakeTime = now;
        
        int shakeX = random.nextInt(-SHAKE_PIXELS, SHAKE_PIXELS + 1);
        int shakeY = random.nextInt(-SHAKE_PIXELS, SHAKE_PIXELS + 1);
        
        long window = mc.getWindow().getHandle();
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        GLFW.glfwGetCursorPos(window, xpos, ypos);
        
        double newX = xpos[0] + shakeX;
        double newY = ypos[0] + shakeY;
        GLFW.glfwSetCursorPos(window, newX, newY);
        
        new Thread(() -> {
            try { Thread.sleep(random.nextInt(30, 50)); } catch (InterruptedException e) {}
            GLFW.glfwSetCursorPos(window, xpos[0], ypos[0]);
        }).start();
    }
    
    private void forceRescanTarget() {
        Entity oldTarget = target;
        updateTarget();
        if (target == oldTarget && target != null && random.nextBoolean()) {
            target = null;
            try { Thread.sleep(random.nextInt(30, 100)); } catch (InterruptedException e) {}
            updateTarget();
        }
    }

    private void updateTarget() {
        if (target != null && target.isAlive() && mc.player.squaredDistanceTo(target) <= RANGE * RANGE) {
            if (random.nextInt(100) < 95) return;
        }
        
        float searchRange = RANGE + random.nextFloat() * 0.6f;
        Entity best = null;
        double closest = searchRange * searchRange;
        Box box = mc.player.getBoundingBox().expand(searchRange);
        List<Entity> entities = mc.world.getOtherEntities(mc.player, box,
                e -> e instanceof LivingEntity && e != mc.player && ((LivingEntity) e).isAlive());
        
        for (Entity e : entities) {
            if (e instanceof PlayerEntity && mc.player.isTeammate((PlayerEntity) e)) continue;
            double dist = mc.player.squaredDistanceTo(e);
            if (dist < closest) {
                if (random.nextFloat() < 0.85f || best == null) {
                    closest = dist;
                    best = e;
                }
            }
        }
        target = best;
    }

    private static float wrap(float v) { 
        v %= 360f; 
        if (v >= 180f) v -= 360f; 
        if (v < -180f) v += 360f; 
        return v; 
    }
    
    private static float clamp(float v, float min, float max) { 
        return Math.max(min, Math.min(max, v)); 
    }

    static class Turns {
        private float yaw, pitch;
        public Turns(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public void setYaw(float y) { yaw = y; }
        public void setPitch(float p) { pitch = p; }
        public Turns adjustSensitivity() { return this; }
    }

    static class MathAngle {
        public static Turns calculateDelta(Turns a, Turns b) {
            float dy = wrap(b.getYaw() - a.getYaw());
            float dp = b.getPitch() - a.getPitch();
            return new Turns(dy, dp);
        }
    }

    static class TSAngle {
        public Turns limitAngleChange(Turns currentAngle, Turns targetAngle, float yawSpeed, float pitchSpeed) {
            Turns delta = MathAngle.calculateDelta(currentAngle, targetAngle);
            float yawDelta = delta.getYaw();
            float pitchDelta = delta.getPitch();

            float yawStep = Math.min(Math.abs(yawDelta), yawSpeed);
            float pitchStep = Math.min(Math.abs(pitchDelta), pitchSpeed);

            float newYaw = currentAngle.getYaw() + Math.signum(yawDelta) * yawStep;
            float newPitch = MathHelper.clamp(currentAngle.getPitch() + Math.signum(pitchDelta) * pitchStep, -89.0F, 90.0F);

            return new Turns(newYaw, newPitch).adjustSensitivity();
        }
    }
}
