package com.example.spotifylyrics;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class SpotifyLyricsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("spotifylyrics");

    @Override
    public void onInitializeClient() {
        SpotifyApi.startPolling();
        HudElementRegistry.addLast(Identifier.of("spotifylyrics", "lyrics"), LyricsHud::render);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    /** Chat-Nachricht, auch aus anderen Threads. */
    static void chat(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> mc.inGameHud.getChatHud().addMessage(text));
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(ClientCommandManager.literal("lyrics")
            .then(ClientCommandManager.literal("clientid")
                .then(ClientCommandManager.argument("id", StringArgumentType.word()).executes(c -> {
                    Config.INSTANCE.clientId = StringArgumentType.getString(c, "id").trim();
                    Config.INSTANCE.save();
                    c.getSource().sendFeedback(Text.literal("Client-ID gespeichert. Jetzt: /lyrics login"));
                    return Command.SINGLE_SUCCESS;
                })))
            .then(ClientCommandManager.literal("login").executes(c -> {
                SpotifyApi.login(
                    url -> chat(Text.literal("Spotify-Login: ").append(
                        Text.literal("[HIER KLICKEN]").setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                            .withFormatting(Formatting.AQUA, Formatting.UNDERLINE)))),
                    msg -> chat(Text.literal(msg)));
                return Command.SINGLE_SUCCESS;
            }))
            .then(ClientCommandManager.literal("logout").executes(c -> {
                SpotifyApi.logout();
                c.getSource().sendFeedback(Text.literal("Von Spotify abgemeldet."));
                return Command.SINGLE_SUCCESS;
            }))
            .then(ClientCommandManager.literal("toggle").executes(c -> {
                Config.INSTANCE.enabled = !Config.INSTANCE.enabled;
                Config.INSTANCE.save();
                c.getSource().sendFeedback(Text.literal("Lyrics-Leiste: " + (Config.INSTANCE.enabled ? "an" : "aus")));
                return Command.SINGLE_SUCCESS;
            }))
            .then(ClientCommandManager.literal("offset")
                .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(-5000, 5000)).executes(c -> {
                    Config.INSTANCE.offsetMs = IntegerArgumentType.getInteger(c, "ms");
                    Config.INSTANCE.save();
                    c.getSource().sendFeedback(Text.literal("Versatz: " + Config.INSTANCE.offsetMs + " ms (positiv = Lyrics früher)"));
                    return Command.SINGLE_SUCCESS;
                })))
            .then(ClientCommandManager.literal("keyword")
                .then(ClientCommandManager.argument("wort", StringArgumentType.word()).executes(c -> {
                    Config.INSTANCE.keyword = StringArgumentType.getString(c, "wort");
                    Config.INSTANCE.save();
                    c.getSource().sendFeedback(Text.literal("Lila Rahmen bei dem Wort: " + Config.INSTANCE.keyword));
                    return Command.SINGLE_SUCCESS;
                }))));
    }
}
