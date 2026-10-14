package net.feelix.stagecoach.client.screen;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
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
        Component locked = Stagecoach.text("config.forced_style", Stagecoach.text("config.style." + ClientConfig.forcedStyle()));
        CycleButton<Boolean> simplified = addRenderableWidget(CycleButton.onOffBuilder(simplifiedLoadingScreen)
                .withTooltip(v -> Tooltip.create(forced ? locked : Stagecoach.text("config.simplified_loading_screen.tooltip")))
                .create(x, y, 200, 20, Stagecoach.text("config.simplified_loading_screen"), (b, v) -> simplifiedLoadingScreen = v));
        CycleButton<Boolean> minimal = addRenderableWidget(CycleButton.onOffBuilder(minimalLoadingScreen)
                .withTooltip(v -> Tooltip.create(forced ? locked : Stagecoach.text("config.minimal_loading_screen.tooltip")))
                .create(x, y + 24, 200, 20, Stagecoach.text("config.minimal_loading_screen"), (b, v) -> minimalLoadingScreen = v));
        simplified.active = !forced;
        minimal.active = !forced;
        addRenderableWidget(CycleButton.onOffBuilder(showOtherStopsOnMap)
                .withTooltip(v -> Tooltip.create(Stagecoach.text("config.show_other_stops.tooltip")))
                .create(x, y + 48, 200, 20, Stagecoach.text("config.show_other_stops"), (b, v) -> showOtherStopsOnMap = v));
        addRenderableWidget(CycleButton.onOffBuilder(showPlacementPreview)
                .withTooltip(v -> Tooltip.create(Stagecoach.text("config.show_placement_preview.tooltip")))
                .create(x, y + 72, 200, 20, Stagecoach.text("config.show_placement_preview"), (b, v) -> showPlacementPreview = v));
        addRenderableWidget(CycleButton.onOffBuilder(discoveryEffects)
                .withTooltip(v -> Tooltip.create(Stagecoach.text("config.discovery_effects.tooltip")))
                .create(x, y + 96, 200, 20, Stagecoach.text("config.discovery_effects"), (b, v) -> discoveryEffects = v));
        addRenderableWidget(CycleButton.onOffBuilder(discoverySound)
                .withTooltip(v -> Tooltip.create(Stagecoach.text("config.discovery_sound.tooltip")))
                .create(x, y + 120, 200, 20, Stagecoach.text("config.discovery_sound"), (b, v) -> discoverySound = v));
        addRenderableWidget(CycleButton.onOffBuilder(placementEffects)
                .withTooltip(v -> Tooltip.create(Stagecoach.text("config.placement_effects.tooltip")))
                .create(x, y + 144, 200, 20, Stagecoach.text("config.placement_effects"), (b, v) -> placementEffects = v));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, Math.min(height - 28, y + 176), 200, 20).build());
    }

    @Override
    public void onClose() {
        ArrayList<String> warnings = new ArrayList<>();
        ClientConfig.save(new ClientConfig(simplifiedLoadingScreen, minimalLoadingScreen, showOtherStopsOnMap, showPlacementPreview,
                discoveryEffects, placementEffects, discoverySound), warnings);
        for (String w : warnings) Stagecoach.LOGGER.warn("config/{}: {}", Stagecoach.CLIENT_CONFIG_FILE, w);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.drawCenteredString(font, title, width / 2, 15, 0xFFFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
