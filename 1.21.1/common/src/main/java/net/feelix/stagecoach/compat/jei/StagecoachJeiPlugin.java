package net.feelix.stagecoach.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@JeiPlugin // Forge finds JEI plugins by this annotation; Fabric by the "jei_mod_plugin" entrypoint
public class StagecoachJeiPlugin implements IModPlugin {
    private static @Nullable IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return Stagecoach.id("jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<ItemStack> coaches = StagecoachRegistry.ITEMS.values().stream().map(ItemStack::new).toList();
        registration.addItemStackInfo(coaches,
                Text.translatable("stagecoach.jei.info.what"),
                Text.translatable("stagecoach.jei.info.place"),
                Text.translatable("stagecoach.jei.info.discover"),
                Text.translatable("stagecoach.jei.info.travel"),
                Text.translatable("stagecoach.jei.info.break"));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Used by the dev screenshot harness to open the info page. */
    public static @Nullable IJeiRuntime runtime() {
        return runtime;
    }
}
