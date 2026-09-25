package mezz.jei.api;

import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.util.Identifier;

public interface IModPlugin {
    Identifier getPluginUid();

    default void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
    }
}
