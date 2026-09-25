package mezz.jei.api;

import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

public interface IModPlugin {
    ResourceLocation getPluginUid();

    default void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
    }
}
