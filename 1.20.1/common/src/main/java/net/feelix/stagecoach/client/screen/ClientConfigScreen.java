package net.feelix.stagecoach.client.screen;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.config.ClientConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class ClientConfigScreen extends Screen {
    private final @Nullable Screen parent;
    private boolean simplifiedLoadingScreen, minimalLoadingScreen, showOtherStopsOnMap, showPlacementPreview;
    private boolean discoveryEffects, placementEffects, discoverySound;

    public ClientConfigScreen(@Nullable Screen parent) {
        super(Stagecoach.text("config.title"));
        this.parent = parent;
        ClientConfig c = ClientConfig.get();
        simplifiedLoadingScreen = c.simplifiedLoadingScreen;
        minimalLoadingScreen = c.minimalLoadingScreen;
        showOtherStopsOnMap = c.showOtherStopsOnMap;
        showPlacementPreview = c.showPlacementPreview;
        discoveryEffects = c.discoveryEffects;
        placementEffects = c.placementEffects;
        discoverySound = c.discoverySound;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100, y = Math.max(32, height / 2 - 92);
        boolean forced = ClientConfig.styleForced();
        Text locked = Stagecoach.text("config.forced_style", Stagecoach.text("config.style." + ClientConfig.forcedStyle()));
        CyclingButtonWidget<Boolean> simplified = addDrawableChild(CyclingButtonWidget.onOffBuilder(simplifiedLoadingScreen)
                .tooltip(v -> Tooltip.of(forced ? locked : Stagecoach.text("config.simplified_loading_screen.tooltip")))
                .build(x, y, 200, 20, Stagecoach.text("config.simplified_loading_screen"), (b, v) -> simplifiedLoadingScreen = v));
        CyclingButtonWidget<Boolean> minimal = addDrawableChild(CyclingButtonWidget.onOffBuilder(minimalLoadingScreen)
                .tooltip(v -> Tooltip.of(forced ? locked : Stagecoach.text("config.minimal_loading_screen.tooltip")))
                .build(x, y + 24, 200, 20, Stagecoach.text("config.minimal_loading_screen"), (b, v) -> minimalLoadingScreen = v));
        simplified.active = !forced;
        minimal.active = !forced;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(showOtherStopsOnMap)
                .tooltip(v -> Tooltip.of(Stagecoach.text("config.show_other_stops.tooltip")))
                .build(x, y + 48, 200, 20, Stagecoach.text("config.show_other_stops"), (b, v) -> showOtherStopsOnMap = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(showPlacementPreview)
                .tooltip(v -> Tooltip.of(Stagecoach.text("config.show_placement_preview.tooltip")))
                .build(x, y + 72, 200, 20, Stagecoach.text("config.show_placement_preview"), (b, v) -> showPlacementPreview = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(discoveryEffects)
                .tooltip(v -> Tooltip.of(Stagecoach.text("config.discovery_effects.tooltip")))
                .build(x, y + 96, 200, 20, Stagecoach.text("config.discovery_effects"), (b, v) -> discoveryEffects = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(discoverySound)
                .tooltip(v -> Tooltip.of(Stagecoach.text("config.discovery_sound.tooltip")))
                .build(x, y + 120, 200, 20, Stagecoach.text("config.discovery_sound"), (b, v) -> discoverySound = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(placementEffects)
                .tooltip(v -> Tooltip.of(Stagecoach.text("config.placement_effects.tooltip")))
                .build(x, y + 144, 200, 20, Stagecoach.text("config.placement_effects"), (b, v) -> placementEffects = v));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(x, Math.min(height - 28, y + 176), 200, 20).build());
    }

    @Override
    public void close() {
        ArrayList<String> warnings = new ArrayList<>();
        ClientConfig.save(new ClientConfig(simplifiedLoadingScreen, minimalLoadingScreen, showOtherStopsOnMap, showPlacementPreview,
                discoveryEffects, placementEffects, discoverySound), warnings);
        for (String w : warnings) Stagecoach.LOGGER.warn("config/{}: {}", Stagecoach.CLIENT_CONFIG_FILE, w);
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
