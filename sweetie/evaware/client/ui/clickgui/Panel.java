package sweetie.evaware.client.ui.clickgui;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import sweetie.evaware.api.event.Listener;
import sweetie.evaware.api.event.events.other.WindowResizeEvent;
import sweetie.evaware.api.module.Category;
import sweetie.evaware.api.module.Module;
import sweetie.evaware.api.module.ModuleManager;
import sweetie.evaware.api.utils.color.UIColors;
import sweetie.evaware.api.utils.animation.AnimationUtil;
import sweetie.evaware.api.utils.animation.Easing;
import sweetie.evaware.api.utils.math.MouseUtil;
import sweetie.evaware.api.utils.render.RenderUtil;
import sweetie.evaware.api.utils.render.ScissorUtil;
import sweetie.evaware.api.utils.render.fonts.Fonts;
import sweetie.evaware.client.ui.UIComponent;
import sweetie.evaware.client.ui.clickgui.module.ModuleComponent;
import sweetie.evaware.client.ui.clickgui.module.SettingComponent;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Panel extends UIComponent {
    private final Category category;
    private final List<ModuleComponent> moduleComponents = new ArrayList<>();

    @Setter private int categoryIndex;

    private double scrollTarget = 0f;
    private final AnimationUtil scrollAnimation = new AnimationUtil();

    public Panel(Category category) {
        this.category = category;

        for (Module module : ModuleManager.getInstance().getModules()) {
            if (module.getCategory() == category) {
                ModuleComponent moduleComponent = new ModuleComponent(module);
                moduleComponent.setRound(getRound() * 2f);
                moduleComponents.add(moduleComponent);
            }
        }

        if (!moduleComponents.isEmpty()) {
            moduleComponents.getLast().setLast(true);
        }

        int index = categoryIndex;
        for (ModuleComponent module : moduleComponents) {
            module.setIndex(index);
            index += 45;
        }

        WindowResizeEvent.getInstance().subscribe(new Listener<>(-1, event -> {
        }));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateThings();
        renderThings(context, mouseX, mouseY, delta);
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        moduleComponents.forEach(m -> m.keyPressed(keyCode, scanCode, modifiers));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!inPanel(mouseX, mouseY)) return;

        for (ModuleComponent module : moduleComponents) {
            if (!MouseUtil.isHovered(mouseX, mouseY, module.getX(), module.getY(), module.getWidth(), module.getHeight())) continue;
            module.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (!inPanel(mouseX, mouseY)) return;

        for (ModuleComponent module : moduleComponents) {
            if (!MouseUtil.isHovered(mouseX, mouseY, module.getX(), module.getY(), module.getWidth(), module.getHeight())) continue;
            module.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY)) return;

        scrollTarget += verticalAmount * 20.0;
        clampScroll();
    }

    private void clampScroll() {
        double maxScroll = 0;
        double contentHeight = calcTotalContentHeight();
        double visibleHeight = getFixedHeight() - getHeaderHeight();
        double minScroll = Math.min(0, visibleHeight - contentHeight);
        scrollTarget = Math.max(minScroll, Math.min(maxScroll, scrollTarget));
    }

    private void updateThings() {
        scrollAnimation.update();
        scrollAnimation.run(scrollTarget, 600, Easing.EXPO_OUT);
        clampScroll();
        setWidth(scaled(99f));
        setHeight(getFixedHeight());
        moduleComponents.forEach(m -> m.setRound(getRound() * 2f));
    }

    private void renderThings(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrixStack = context.getMatrices();
        float fontSize = getHeaderHeight() * 0.475f;
        int fullAlpha = (int) (getAlpha() * 255f);

        RenderUtil.BLUR_RECT.draw(matrixStack, getX(), getY(), getWidth(), getFixedHeight(),
                new Vector4f(getRound(), getRound(), getRound(), getRound()), UIColors.blur(fullAlpha));

        RenderUtil.BLUR_RECT.draw(matrixStack, getX(), getY(), getWidth(), getHeaderHeight(),
                new Vector4f(getRound(), getRound(), 0f, 0f), UIColors.blur(fullAlpha));

        Fonts.PS_BOLD.drawCenteredText(matrixStack, category.getLabel(),
                getX() + getWidth() / 2f,
                getY() + getHeaderHeight() / 2f - fontSize / 2f,
                fontSize, UIColors.textColor(fullAlpha));

        calcModules();

        float scissorY = getY() + getHeaderHeight();
        float scissorH = getFixedHeight() - getHeaderHeight();
        ScissorUtil.start(matrixStack, getX(), scissorY, getWidth(), scissorH);

        for (ModuleComponent module : moduleComponents) {
            if (module.getY() + module.getHeight() < scissorY) continue;
            if (module.getY() > scissorY + scissorH) continue;
            module.setAlpha(getAlpha());
            module.render(context, mouseX, mouseY, delta);
        }

        ScissorUtil.stop(matrixStack);
    }

    private void calcModules() {
        float moduleY = 0f;
        double scroll = scrollAnimation.getValue();

        for (ModuleComponent module : moduleComponents) {
            float openAnim = module.getAnim();
            if (openAnim > 0f) {
                float settingOffset = 0f;
                for (SettingComponent setting : module.getSettings()) {
                    float visibleAnim = (float) setting.getVisibleAnimation().getValue();
                    if (visibleAnim > 0f) {
                        settingOffset += (setting.getHeight() + gap()) * visibleAnim;
                    }
                }
                settingOffset *= openAnim;
                module.setHeight(module.getDefaultHeight() + (settingOffset + gap()) * openAnim);
            } else {
                module.setHeight(module.getDefaultHeight());
            }

            module.setWidth(getWidth());
            module.setRound(getRound() / 2f);
            module.setX(getX());
            module.setY((float) (getY() + getHeaderHeight() + scroll + moduleY));
            moduleY += module.getHeight();
        }
    }

    private float calcTotalContentHeight() {
        float total = 0f;
        for (ModuleComponent module : moduleComponents) total += module.getHeight();
        return total;
    }

    public float getHeaderHeight() {
        return scaled(18f);
    }

    public float getFixedHeight() {
        return scaled(240f);
    }

    public float getRound() {
        return getHeaderHeight() / 2.2f;
    }

    public boolean inPanel(double mouseX, double mouseY) {
        return MouseUtil.isHovered(mouseX, mouseY, getX(), getY() + getHeaderHeight(), getWidth(), getFixedHeight() - getHeaderHeight());
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return MouseUtil.isHovered(mouseX, mouseY, getX(), getY(), getWidth(), getFixedHeight());
    }
}
