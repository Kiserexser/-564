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
    private static int attackCount = 0;
    
    // HT-1 обходы
    private static final float ROTATION_NOISE_STRENGTH = 0.33f;
    private static long lastNoiseTime = 0;
    private static int fakeLagCounter = 0;
    private static boolean desyncMode = false;
    private static long lastHackDetectorPing = 0;
    private static long lastDesyncUpdate = 0;
    
    // Смена цели: тело/голова
    private static boolean aimHead = false; // false = тело, true = голова
    private static long lastAimSwitchTime = 0;
    private static final long AIM_SWITCH_INTERVAL = 5000; // 5 секунд

    // ========== НАСТРОЙКИ ==========
    private static final float RANGE = 4.2f;
    private static final long MIN_DELAY = 650L;      // 0.650 сек
    private static final long MAX_DELAY = 700L;      // 0.700 сек
    private static final float YAW_SPEED_BASE = 25.0f;
    private static final float YAW_SPEED_VARIATION = 12.0f;
    private static final float PITCH_SPEED_FACTOR_MIN = 0.4f;
    private static final float PITCH_SPEED_FACTOR_MAX = 0.9f;
    private static final int SHAKE_PIXELS = 3;       // тряска 3 пикселя
    private static final long SHAKE_INTERVAL_MS = 50;

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
                    mc.player.sendMessage(Text.literal(killaura ? "§aKillaura (HT-1 EVASION) ON" : "§cKillaura OFF"), true);
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
        
        // Смена цели между телом и головой каждые 5 секунд
        updateAimTarget();
        
        // FAKE LAG
        if (fakeLagCounter > 0) {
            fakeLagCounter--;
            try { Thread.sleep(random.nextInt(1, 3)); } catch (InterruptedException e) {}
            return;
        }
        if (random.nextInt(180) < 2) {
            fakeLagCounter = random.nextInt(1, 4);
            return;
        }
        
        // ROTATION NOISE
        applyRotationNoise();
        
        // DESYNC ROTATION
        applyRotationDesync();
        
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
        Vec3d targetPoint;
        
        // ВЫБОР ЦЕЛИ: тело (центр хитбокса) или голова (+0.8 по Y)
        if (aimHead) {
            // Аим на голову (верхняя часть хитбокса)
            targetPoint = target.getBoundingBox().getCenter().add(0, 0.4, 0);
        } else {
            // Аим на тело (центр)
            targetPoint = target.getBoundingBox().getCenter();
        }
        
        float aimOffsetX = (random.nextFloat() - 0.5f) * 0.06f;
        float aimOffsetY = (random.nextFloat() - 0.5f) * 0.04f;
        float aimOffsetZ = (random.nextFloat() - 0.5f) * 0.04f;
        Vec3d to = targetPoint.add(aimOffsetX, aimOffsetY, aimOffsetZ).subtract(eye);
        
        double hyp = Math.hypot(to.x, to.z);
        float idealYaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
        float idealPitch = (float) -Math.toDegrees(Math.atan2(to.y, hyp));
        idealYaw = wrap(idealYaw);
        idealPitch = clamp(idealPitch, -89, 89);

        // HUMANIZED MICRO-JITTER
        float jitterYaw = (random.nextFloat() - 0.5f) * 0.18f;
        float jitterPitch = (random.nextFloat() - 0.5f) * 0.12f;
        idealYaw += jitterYaw;
        idealPitch += jitterPitch;
        idealYaw = wrap(idealYaw);
        idealPitch = clamp(idealPitch, -89, 89);

        float dynamicYawSpeed = YAW_SPEED_BASE + random.nextFloat() * YAW_SPEED_VARIATION;
        float dynamicPitchSpeed = dynamicYawSpeed * (PITCH_SPEED_FACTOR_MIN + random.nextFloat() * (PITCH_SPEED_FACTOR_MAX - PITCH_SPEED_FACTOR_MIN));
        
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        
        float yawDelta = wrap(idealYaw - currentYaw);
        float pitchDelta = idealPitch - currentPitch;
        
        float yawStep = Math.min(Math.abs(yawDelta), dynamicYawSpeed);
        float pitchStep = Math.min(Math.abs(pitchDelta), dynamicPitchSpeed);
        
        float newYaw = currentYaw + Math.signum(yawDelta) * yawStep;
        float newPitch = MathHelper.clamp(currentPitch + Math.signum(pitchDelta) * pitchStep, -89.0F, 90.0F);
        
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        mc.player.headYaw = newYaw;
        mc.player.bodyYaw = newYaw;

        long now = System.currentTimeMillis();
        long baseDelay = MIN_DELAY + (long)(random.nextDouble() * (MAX_DELAY - MIN_DELAY));
        long fatigueDelay = (long)(baseDelay + (attackCount / 12) * 2);
        fatigueDelay = Math.min(fatigueDelay, MAX_DELAY + 30);
        
        float deltaYawCheck = wrap(idealYaw - mc.player.getYaw());
        float deltaPitchCheck = idealPitch - mc.player.getPitch();
        float angleTolerance = 8.0f + random.nextFloat() * 7.0f;
        boolean canAttack = Math.abs(deltaYawCheck) < angleTolerance && Math.abs(deltaPitchCheck) < angleTolerance;
        
        if (now - lastAttackTime >= fatigueDelay && canAttack) {
            boolean wasSprinting = mc.player.isSprinting();
            
            if (random.nextInt(100) < 18) {
                try { Thread.sleep(random.nextInt(1, 12)); } catch (InterruptedException e) {}
            }
            
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            
            if (wasSprinting) mc.player.setSprinting(true);
            mc.player.setSprinting(true);
            
            lastAttackTime = now;
            attackCount++;
            
            if (desyncMode && attackCount % 3 == 0) {
                desyncMode = false;
                new Thread(() -> {
                    try { Thread.sleep(random.nextInt(50, 150)); } catch (InterruptedException e) {}
                    desyncMode = true;
                }).start();
            }
            
            if (attackCount % 7 == 0 && random.nextBoolean()) {
                forceRescanTarget();
            }
        }
        
        // АНТИ-ПАТТЕРН
        if (random.nextInt(380) < 2) {
            try { Thread.sleep(random.nextInt(20, 70)); } catch (InterruptedException e) {}
        }
        
        // Эмуляция честного игрока
        if (now - lastHackDetectorPing > 5000) {
            lastHackDetectorPing = now;
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("/ping"), true);
            }
        }
    }
    
    private void updateAimTarget() {
        long now = System.currentTimeMillis();
        if (now - lastAimSwitchTime >= AIM_SWITCH_INTERVAL) {
            aimHead = !aimHead; // переключаем между телом и головой
            lastAimSwitchTime = now;
            
            // Отладочное сообщение в чат (можно убрать, но оставил для наглядности)
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("§7[Killaura] Aiming: " + (aimHead ? "§6HEAD" : "§3BODY")), true);
            }
        }
    }
    
    private void applyRotationNoise() {
        long now = System.currentTimeMillis();
        if (now - lastNoiseTime < 18) return;
        lastNoiseTime = now;
        
        if (random.nextInt(100) < 35 && mc.player != null) {
            float noiseYaw = (random.nextFloat() - 0.5f) * ROTATION_NOISE_STRENGTH;
            float noisePitch = (random.nextFloat() - 0.5f) * ROTATION_NOISE_STRENGTH;
            mc.player.setYaw(wrap(mc.player.getYaw() + noiseYaw));
            mc.player.setPitch(clamp(mc.player.getPitch() + noisePitch, -89, 89));
        }
    }
    
    private void applyRotationDesync() {
        long now = System.currentTimeMillis();
        if (now - lastDesyncUpdate < 200) return;
        lastDesyncUpdate = now;
        
        if (random.nextInt(100) < 12) {
            desyncMode = !desyncMode;
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
        if (mc.player == null || mc.world == null) return;
        
        if (target != null && target.isAlive() && mc.player.squaredDistanceTo(target) <= RANGE * RANGE) {
            if (random.nextInt(100) < 94) return;
        }
        
        float searchRange = RANGE + random.nextFloat() * 0.7f;
        Entity best = null;
        double closest = searchRange * searchRange;
        Box box = mc.player.getBoundingBox().expand(searchRange);
        List<Entity> entities = mc.world.getOtherEntities(mc.player, box,
                e -> e instanceof LivingEntity && e != mc.player && ((LivingEntity) e).isAlive());
        
        for (Entity e : entities) {
            if (e instanceof PlayerEntity && mc.player.isTeammate((PlayerEntity) e)) continue;
            double dist = mc.player.squaredDistanceTo(e);
            if (dist < closest) {
                if (random.nextFloat() < 0.83f || best == null) {
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
}
