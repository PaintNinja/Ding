package me.ichun.mods.ding.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Ding.MOD_ID)
public final class Ding
{
    public static final String MOD_ID = "ding";
    public static final String MOD_NAME = "Ding";

    private static final Logger LOGGER = LogManager.getLogger();

    public static Config config;

    public Ding()
    {
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
            setupConfig();
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::finishLoading);
        });
        DistExecutor.runWhenOn(Dist.DEDICATED_SERVER, () -> () -> LOGGER.log(Level.ERROR, "You are loading " + MOD_NAME + " on a server. " + MOD_NAME + " is a client only mod!"));

        //Make sure the mod being absent on the other network side does not cause the client to display the server as incompatible
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    private void setupConfig()
    {
        //build the config
        ForgeConfigSpec.Builder configBuilder = new ForgeConfigSpec.Builder();

        config = new Config(configBuilder);

        //register the config. This loads the config for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, configBuilder.build(), MOD_ID + ".toml");
    }

    private void finishLoading(final FMLLoadCompleteEvent event)
    {
        EventHandler.postInit = true;
    }

    public static class Config
    {
        public final ForgeConfigSpec.ConfigValue<String> name, nameWorld;
        public final ForgeConfigSpec.DoubleValue pitch, pitchWorld;

        public final ForgeConfigSpec.IntValue playOn;

        public Config(final ForgeConfigSpec.Builder builder)
        {
            builder.comment("Configs related to how ding works").push("ding");

            name = builder.comment("Resource Location based name of the sound file to play when Minecraft finishes loading.\nEG: \"ui.button.click\" or \"entity.experience_orb.pickup\"\n\nThis can also be a mod sound if the mod is installed.\nEG: \"modname:modsound.boing\"\n\nIf you want to use external sounds, consider looking into the mod Additional Resources")
                    .translation("config.ding.prop.name.desc")
                    .define("name", "entity.experience_orb.pickup");
            pitch = builder.comment("Pitch of the sound (when Minecraft loads)")
                    .translation("config.ding.prop.pitch.desc")
                    .defineInRange("pitch", 1D, 0D, 10D);

            nameWorld = builder.comment("Resource Location based name of the sound file to play when the world finishes loading.\nEG: \"ui.button.click\" or \"entity.experience_orb.pickup\"\n\nThis can also be a mod sound if the mod is installed.\nEG: \"modname:modsound.boing\"\n\nIf you want to use external sounds, consider looking into the mod Additional Resources")
                    .translation("config.ding.prop.nameWorld.desc")
                    .define("nameWorld", "entity.experience_orb.pickup");
            pitchWorld = builder.comment("Pitch of the sound (when the world loads)")
                    .translation("config.ding.prop.pitchWorld.desc")
                    .defineInRange("pitchWorld", 1D, 0D, 10D);

            playOn = builder.comment("Play sound on...\n0 = Nothing (why install the mod though?)\n1 = MC load\n2 = World load\n3 = MC and World load")
                    .translation("config.ding.prop.playOn.desc")
                    .defineInRange("playOn", 1, 0, 3);

            builder.pop();
        }
    }

    @Mod.EventBusSubscriber(Dist.CLIENT)
    public static class EventHandler
    {
        public static boolean postInit, played, playWorld;

        @SubscribeEvent
        public static void onGuiOpen(final GuiOpenEvent event)
        {
            if(postInit && event.getGui() instanceof MainMenuScreen && !played)
            {
                played = true;
                final int playOn = config.playOn.get();
                if((playOn & 1) > 0)
                {
                    final String name = config.name.get();
                    final SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(name));
                    if(sound != null)
                    {
                        Minecraft.getInstance().getSoundHandler().play(SimpleSound.master(sound, config.pitch.get().floatValue()));
                    }
                    else
                    {
                        LOGGER.log(Level.WARN, "Could not find sound: {}", new ResourceLocation(name));
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onClientLoggedInEvent(final ClientPlayerNetworkEvent.LoggedInEvent event)
        {
            playWorld = true;
        }

        @SubscribeEvent
        public static void onWorldTick(final TickEvent.WorldTickEvent event)
        {
            final Minecraft mcInstance = Minecraft.getInstance();

            if(playWorld && event.phase == TickEvent.Phase.END && mcInstance.player != null && (mcInstance.player.ticksExisted > 20 || mcInstance.isGamePaused()))
            {
                playWorld = false;
                final int playOn = config.playOn.get();
                if((playOn & 2) > 0)
                {
                    final String nameWorld = config.nameWorld.get();
                    final SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(nameWorld));
                    if(sound != null)
                    {
                        mcInstance.getSoundHandler().play(SimpleSound.master(sound, config.pitchWorld.get().floatValue()));
                    }
                    else
                    {
                        LOGGER.log(Level.WARN, "Could not find sound: {}", new ResourceLocation(nameWorld));
                    }
                }
            }
        }
    }
}
