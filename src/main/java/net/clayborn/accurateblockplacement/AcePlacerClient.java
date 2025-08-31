package net.clayborn.accurateblockplacement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.jarjar.nio.util.Lazy;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

import java.util.function.Supplier;


// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(value = AcePlacerClient.MODID, dist = Dist.CLIENT)
public class AcePlacerClient {
    public static final String MODID = "aceplacer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static boolean disableNormalItemUse = false;
    public static boolean isAccurateBlockPlacementEnabled = true;

    public static final Supplier<KeyMapping> keybind = () -> new KeyMapping(
            "net.clayborn.accurateblockplacement.togglevanillaplacement", // Will be localized using this translation key
            InputConstants.Type.KEYSYM,
            -1,
            "Accurate Block Placement"
    );

    public AcePlacerClient(IEventBus modEventBus, ModContainer modContainer) {
    }
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        AcePlacerClient.LOGGER.info("HELLO FROM CLIENT SETUP");
        AcePlacerClient.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
    @SubscribeEvent
    public static void registerBindings(RegisterKeyMappingsEvent event) {
        event.register(keybind.get());
    }
    @SubscribeEvent
    public static void registerBindings(ClientTickEvent.Post event) {
        while (keybind.get().consumeClick()){
            isAccurateBlockPlacementEnabled = !isAccurateBlockPlacementEnabled;
            final MutableComponent message;
            if (isAccurateBlockPlacementEnabled) {
                message = Component.translatable("net.clayborn.accurateblockplacement.modplacementmodemessage");
            } else {
                message = Component.translatable("net.clayborn.accurateblockplacement.vanillaplacementmodemessage");
            }
            Minecraft.getInstance().gui.getChat().addMessage(message);
        }
    }
}
