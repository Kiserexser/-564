package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // Включение/выключение по клавише R
    private static boolean killaura = false;
    private static boolean lastR = false;

    // Цель
    private static Entity target = null;

    // --- Задержка между ударами 680-740 мс ---
    private static long lastAttackTime = 0;
    private static final long MIN_DELAY = 680L;
    private static final long MAX_DELAY = 740L;
    private static int attackCount = 0;

    // --- Тряска экрана 3 пикселя ---
    private static long lastScreenShakeTime = 0;
    private static final int SHAKE_PIXELS = 3;

    // --- Смена прицела тело/голова каждые 5 секунд ---
    private static boolean aimHead = false;
    private static long lastAimSwitchTime = 0;
    private static final long AIM_SWITCH_INTERVAL = 5000;

    // --- Rotation noise (микро-шум 0.33°) ---
    private static long lastNoiseTime = 0;
    private static final float NOISE_STRENGTH = 0.33f;

    // --- Параметры ротации (оригинальные из LGAngle) ---
    private static final float RANGE = 4.2f;

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
                    mc.player.sendMessage(Text.literal(killaura ? "§aKillaura ON" : "§cKillaura OFF"), true);
                    if (!killaura) target = null;
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;
                if (killaura) tick();
            }
        }).start();
    }

    private void tick() {
        // --- Тряска экрана ---
        shakeScreen();

        // --- Rotation noise ---
        applyNoise();

        // --- Смена тело/голова ---
        updateAimMode();

        // --- Поиск цели ---
        updateTarget();
        if (target == null) return;

        // --- Проверка дистанции ---
        if (mc.player.squaredDistanceTo(target) > RANGE * RANGE) return;

        // --- Ротация (оригинальная логика LGAngle) ---
        performRotation(target);

        // --- Атака с задержкой 680-740 мс ---
        long now = System.currentTimeMillis();
        long baseDelay = MIN_DELAY + (long)(random.nextDouble() * (MAX_DELAY - MIN_DELAY));
        long fatigue = Math.min(baseDelay + (attackCount / 12) * 2, MAX_DELAY + 30);
        boolean canHit = mc.player.canSee(target);

        if (now - lastAttackTime >= fatigue && canHit) {
            // микро-пауза перед ударом
            if (random.nextInt(100) < 18) {
                try { Thread.sleep(random.nextInt(1, 12)); } catch (InterruptedException ignored) {}
            }
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            // восстанавливаем спринт
            if (mc.player.isSprinting()) mc.player.setSprinting(true);
            lastAttackTime = now;
            attackCount++;
        }
    }

    // === ОРИГИНАЛЬНАЯ РОТАЦИЯ LGAngle (синусоидальный шум, адаптивная скорость) ===
    private void performRotation(Entity target) {
        // Получаем точку прицеливания (тело или голова)
        Vec3d aimPoint = aimHead ?
                target.getBoundingBox().getCenter().add(0, 0.4, 0) :
                target.getBoundingBox().getCenter();

        Vec3d eye = mc.player.getEyePos();
        Vec3d to = aimPoint.subtract(eye);
        double hyp = Math.hypot(to.x, to.z);
        float idealYaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
        float idealPitch = (float) -Math.toDegrees(Math.atan2(to.y, hyp));
        idealYaw = wrap(idealYaw);
        idealPitch = clamp(idealPitch, -89, 89);

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float yawDelta = wrap(idealYaw - currentYaw);
        float pitchDelta = idealPitch - currentPitch;

        // Оригинальные расчёты LGAngle
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        if (rotationDifference < 0.01) return;

        // Адаптивная скорость (симулируем `атака` из оригинала как random)
        boolean attacking = random.nextFloat() < 0.3;
        float speed = attacking ? 0.45F : (random.nextFloat() < 0.5 ? 1.0F : 0.3F);
        if (attacking && !mc.player.canSee(target)) speed = 0.45F;

        float lineYaw = (Math.abs(yawDelta / rotationDifference) * 180);
        float linePitch = (Math.abs(pitchDelta) * 180);
        float moveYaw = MathHelper.clamp(yawDelta, -lineYaw, lineYaw);
        float movePitch = MathHelper.clamp(pitchDelta, -linePitch, linePitch);
        float targetYaw = currentYaw + moveYaw;

        // Синусоидальный шум (как в оригинале)
        float yawNoise = 0;
        float pitchNoise = 0;
        if (!attacking) {
            double градуси = 10.0;
            double period = 10.0;
            double максамп = 10.0;
            double радс = Math.toRadians(градуси);
            double t = System.currentTimeMillis() / (period * 55);
            double амп = максамп * Math.sin(t * Math.PI * 2);
            yawNoise = (float) (амп * Math.cos(радс));
            pitchNoise = (float) (амп * Math.sin(радс));
            pitchNoise = MathHelper.clamp(pitchNoise, -90.0f, 90.0f);
        }

        float newYaw = MathHelper.lerp(randomLerp(speed, speed + 0.2F), currentYaw, targetYaw) + yawNoise;
        float newPitch = MathHelper.lerp(randomLerp(speed, speed + 0.2F), currentPitch, currentPitch + movePitch) + pitchNoise;
        newPitch = MathHelper.clamp(newPitch, -90.0f, 90.0f);

        // Применяем поворот
        mc.player.setYaw(wrap(newYaw));
        mc.player.setPitch(newPitch);
        mc.player.headYaw = newYaw;
        mc.player.bodyYaw = newYaw;
    }

    private float randomLerp(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    // === ПОИСК ЦЕЛИ ===
    private void updateTarget() {
        if (target != null && target.isAlive() && mc.player.squaredDistanceTo(target) <= RANGE * RANGE) {
            if (random.nextInt(100) < 90) return;
        }
        Entity best = null;
        double closest = RANGE * RANGE;
        Box box = mc.player.getBoundingBox().expand(RANGE);
        List<Entity> entities = mc.world.getOtherEntities(mc.player, box,
                e -> e instanceof LivingEntity && e != mc.player && ((LivingEntity) e).isAlive());
        for (Entity e : entities) {
            if (e instanceof PlayerEntity && mc.player.isTeammate((PlayerEntity) e)) continue;
            double dist = mc.player.squaredDistanceTo(e);
            if (dist < closest && mc.player.canSee(e)) {
                closest = dist;
                best = e;
            }
        }
        target = best;
    }

    // === ТРЯСКА ЭКРАНА (3 пикселя) ===
    private void shakeScreen() {
        if (mc.getWindow() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScreenShakeTime < 50) return;
        lastScreenShakeTime = now;
        int shakeX = random.nextInt(-SHAKE_PIXELS, SHAKE_PIXELS + 1);
        int shakeY = random.nextInt(-SHAKE_PIXELS, SHAKE_PIXELS + 1);
        long window = mc.getWindow().getHandle();
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        GLFW.glfwSetCursorPos(window, x[0] + shakeX, y[0] + shakeY);
        new Thread(() -> {
            try { Thread.sleep(40); } catch (InterruptedException ignored) {}
            GLFW.glfwSetCursorPos(window, x[0], y[0]);
        }).start();
    }

    // === ROTATION NOISE ===
    private void applyNoise() {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastNoiseTime < 18) return;
        lastNoiseTime = now;
        if (random.nextInt(100) < 35) {
            float noiseYaw = (random.nextFloat() - 0.5f) * NOISE_STRENGTH;
            float noisePitch = (random.nextFloat() - 0.5f) * NOISE_STRENGTH;
            mc.player.setYaw(wrap(mc.player.getYaw() + noiseYaw));
            mc.player.setPitch(clamp(mc.player.getPitch() + noisePitch, -89, 89));
        }
    }

    // === СМЕНА ТЕЛО/ГОЛОВА ===
    private void updateAimMode() {
        long now = System.currentTimeMillis();
        if (now - lastAimSwitchTime >= AIM_SWITCH_INTERVAL) {
            aimHead = !aimHead;
            lastAimSwitchTime = now;
        }
    }

    private float wrap(float v) {
        v %= 360;
        if (v >= 180) v -= 360;
        if (v < -180) v += 360;
        return v;
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
