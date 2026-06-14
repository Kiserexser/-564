package fun.rich.utils.features.aura.rotations.impl;

import fun.rich.Rich;
import fun.rich.features.impl.combat.Aura;
import fun.rich.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.rich.utils.features.aura.striking.StrikeManager;
import fun.rich.utils.features.aura.utils.MathAngle;
import fun.rich.utils.features.aura.warp.Turns;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.security.SecureRandom;
import java.util.Random;

public class LGAngle extends RotateConstructor {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // Обход 1: задержка между ударами 650-700 мс
    private static long lastAttackTime = 0;
    private static final long MIN_DELAY = 650L;
    private static final long MAX_DELAY = 700L;
    private static int attackCount = 0;

    // Обход 2: тряска экрана 3 пикселя
    private static long lastScreenShakeTime = 0;
    private static final int SHAKE_PIXELS = 3;

    // Обход 3: смена прицела тело/голова каждые 5 секунд
    private static boolean aimHead = false;
    private static long lastAimSwitchTime = 0;
    private static final long AIM_SWITCH_INTERVAL = 5000;

    // Обход 4: rotation noise (микро-шум 0.33°)
    private static long lastNoiseTime = 0;
    private static final float NOISE_STRENGTH = 0.33f;

    public LGAngle() {
        super("CakeWorld");
    }

    @Override
    public Turns limitAngleChange(Turns currentAngle, Turns targetAngle, Vec3d vec3d, Entity entity) {
        // --- ТРЯСКА ЭКРАНА ---
        shakeScreen();

        // --- ROTATION NOISE ---
        applyNoise();

        // --- СМЕНА ТЕЛО/ГОЛОВА ---
        updateAimMode();

        Aura aura = Aura.getInstance();

        // --- ПОДМЕНА ТОЧКИ ПРИЦЕЛИВАНИЯ (тело/голова) ---
        if (aura.getTarget() != null && mc.player != null) {
            Vec3d aimPoint = aimHead ?
                    aura.getTarget().getBoundingBox().getCenter().add(0, 0.4, 0) :
                    aura.getTarget().getBoundingBox().getCenter();
            Vec3d eye = mc.player.getEyePos();
            Vec3d to = aimPoint.subtract(eye);
            double hyp = Math.hypot(to.x, to.z);
            float newYaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
            float newPitch = (float) -Math.toDegrees(Math.atan2(to.y, hyp));
            targetAngle.setYaw(wrap(newYaw));
            targetAngle.setPitch(clamp(newPitch, -89, 89));
        }

        // --- АТАКА С ЗАДЕРЖКОЙ 650-700 мс (микро-пауза, усталость) ---
        long now = System.currentTimeMillis();
        long baseDelay = MIN_DELAY + (long)(random.nextDouble() * (MAX_DELAY - MIN_DELAY));
        long fatigue = Math.min(baseDelay + (attackCount / 12) * 2, MAX_DELAY + 30);
        boolean canHit = aura.getTarget() != null && mc.player != null &&
                mc.player.squaredDistanceTo(aura.getTarget()) <= aura.getAttackRange().getValue() * aura.getAttackRange().getValue() &&
                mc.player.canSee(aura.getTarget());

        if (now - lastAttackTime >= fatigue && canHit) {
            // микро-пауза перед ударом (человеческий фактор)
            if (random.nextInt(100) < 18) {
                try { Thread.sleep(random.nextInt(1, 12)); } catch (InterruptedException ignored) {}
            }
            mc.interactionManager.attackEntity(mc.player, aura.getTarget());
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            if (mc.player.isSprinting()) mc.player.setSprinting(true);
            lastAttackTime = now;
            attackCount++;
        }

        // ===== ОРИГИНАЛЬНАЯ ЛОГИКА LGAngle (НЕ ТРОГАТЬ) =====
        Turns angleDelta = MathAngle.calculateDelta(currentAngle, targetAngle);
        float yawDelta = angleDelta.getYaw(), pitchDelta = angleDelta.getPitch();
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));

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

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ОБХОДОВ ===
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
